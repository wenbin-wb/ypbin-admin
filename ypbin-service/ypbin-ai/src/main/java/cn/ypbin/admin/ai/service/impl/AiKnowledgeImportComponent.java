/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service.impl;

import cn.ypbin.admin.ai.model.req.AiDocumentImportReq;
import cn.ypbin.admin.ai.model.resp.AiDocumentVO;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.util.LogSanitizer;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 知识库 URL / Sitemap / RSS 网络导入组件。
 *
 * <p>承载网页抓取、Sitemap 批量解析与 RSS 订阅导入能力，从
 * {@link AiKnowledgeBizServiceImpl} 拆分，保持单一职责。</p>
 *
 * <p>网络导入为服务端发起的 HTTP 请求（SSRF 面）：所有入站 URL 先经协议白名单（仅
 * http/https）与目标地址黑名单（环回/内网/链路本地/保留/组播网段等）校验后再连接；
 * 抓取显式设置连接超时与响应体大小上限；禁止跟随重定向（防止经 302 跳转绕过域名校验）。
 * DNS 在解析校验与实际连接之间可能被再次解析（DNS rebinding），无法彻底根治，已按
 * 最小残留风险处理——域名只要任一解析结果命中黑名单即整体拒绝。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class AiKnowledgeImportComponent {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeImportComponent.class);

    /** Sitemap 单次导入 URL 上限 */
    private static final int SITEMAP_MAX_URLS = 100;

    /** RSS 单次导入尝试条数上限（成功与失败均计数，防止条目连续失败时无界抓取） */
    private static final int RSS_MAX_ATTEMPTS = 50;

    /** 重定向拒绝时的可读提示 */
    private static final String REDIRECT_REJECT_HINT = "（已拒绝跟随重定向，请使用最终直达地址）";

    private final AiKnowledgeCrudComponent crudComponent;

    /** 统一收口的抓取客户端：内置 SSRF 校验与瞬时故障重试 */
    private final AiUrlFetcher urlFetcher;

    public List<AiDocumentVO> importFromUrl(Long knowledgeBaseId, AiDocumentImportReq req) {
        crudComponent.requireKb(knowledgeBaseId);
        String srcType = req.getSourceType().toUpperCase();
        return switch (srcType) {
            case "URL" -> List.of(importSingleUrl(knowledgeBaseId, req.getUrl(),
                req.getCustomTitle()));
            case "SITEMAP" -> importSitemap(knowledgeBaseId, req.getUrl(),
                req.getMaxUrls());
            case "RSS" -> importRss(knowledgeBaseId, req.getUrl());
            default -> throw new BusinessException("不支持的导入类型：" + req.getSourceType());
        };
    }

    /**
     * 抓取单个网页，提取正文后落库并异步向量化，返回文档 VO。
     */
    private AiDocumentVO importSingleUrl(Long knowledgeBaseId, String url, String customTitle) {
        String content;
        String title;
        try {
            Connection.Response response = urlFetcher.fetch(url);
            var htmlDoc = response.parse();
            title = (customTitle != null && !customTitle.isBlank())
                ? customTitle : htmlDoc.title();
            if (title.isBlank()) {
                title = url;
            }
            // 提取正文：优先 article/main/[role=main]，退回 body
            Element main = htmlDoc.selectFirst("article,main,[role=main]");
            content = (main != null ? main : htmlDoc.body()).text();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ypbin-ai] 抓取 URL 失败: url={}", LogSanitizer.sanitize(url), e);
            throw new BusinessException("抓取 URL 失败：" + url);
        }
        if (content.isBlank()) {
            throw new BusinessException("页面内容为空，无法导入：" + url);
        }
        return crudComponent.createDocFromText(knowledgeBaseId, title + ".md", url, "URL",
            content.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析 Sitemap（支持 sitemapindex），逐个 URL 抓取导入，最多 maxUrls 条 */
    private List<AiDocumentVO> importSitemap(Long knowledgeBaseId, String sitemapUrl,
            Integer maxUrls) {
        int limit = (maxUrls == null || maxUrls < 1) ? 10 : Math.min(maxUrls, SITEMAP_MAX_URLS);
        List<String> urls;
        try {
            // 站点地图为 XML：内容类型忽略校验
            Connection.Response response = urlFetcher.fetchIgnoringContentType(sitemapUrl);
            var xml = response.parse();
            // 优先 urlset 的 loc；若为 sitemapindex，则只取一层子 sitemap 的 url（递归深度封顶 2 层）
            Elements locs = xml.select("urlset > url > loc");
            if (locs.isEmpty()) {
                Elements indexLocs = xml.select("sitemapindex > sitemap > loc");
                for (Element sub : indexLocs) {
                    if (urlsSizeGuard(locs)) {
                        break;
                    }
                    try {
                        Connection.Response subResponse =
                            urlFetcher.fetchIgnoringContentType(sub.text());
                        locs.addAll(subResponse.parse().select("urlset > url > loc"));
                    } catch (Exception e) {
                        log.error("[ypbin-ai] sitemap 子文件解析失败: url={}", sub.text(), e);
                    }
                }
            }
            urls = locs.stream().map(Element::text).limit(limit).toList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ypbin-ai] 解析 Sitemap 失败: url={}", LogSanitizer.sanitize(sitemapUrl), e);
            throw new BusinessException("解析 Sitemap 失败：" + sitemapUrl);
        }
        if (urls.isEmpty()) {
            throw new BusinessException("Sitemap 中未找到有效 URL");
        }
        List<AiDocumentVO> results = new ArrayList<>();
        for (String u : urls) {
            try {
                results.add(importSingleUrl(knowledgeBaseId, u, null));
            } catch (Exception e) {
                log.error("[ypbin-ai] Sitemap 导入跳过 URL 失败: url={}", u, e);
            }
        }
        return results;
    }

    private boolean urlsSizeGuard(Elements locs) {
        return locs.size() >= SITEMAP_MAX_URLS;
    }

    /** 解析 RSS/Atom Feed，将每篇文章导入为一个文档 */
    private List<AiDocumentVO> importRss(Long knowledgeBaseId, String feedUrl) {
        List<SyndEntry> entries;
        try {
            // Feed 以文本抓取（受超时/大小上限/目标校验约束），再交给 Rome 解析，避免直接 URL 抓取无约束
            Connection.Response response = urlFetcher.fetchIgnoringContentType(feedUrl);
            SyndFeed feed = new SyndFeedInput().build(new StringReader(response.body()));
            entries = feed.getEntries() != null ? feed.getEntries() : List.of();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ypbin-ai] 解析 RSS/Atom 失败: url={}", LogSanitizer.sanitize(feedUrl), e);
            throw new BusinessException("解析 RSS/Atom 失败：" + feedUrl);
        }
        if (entries.isEmpty()) {
            throw new BusinessException("RSS 中没有文章条目");
        }
        List<AiDocumentVO> results = new ArrayList<>();
        int attempted = 0;
        for (SyndEntry entry : entries) {
            // 以「已尝试条数」封顶（成功与失败均计数），失败条目不逃逸上限导致无界重试
            if (attempted >= RSS_MAX_ATTEMPTS) {
                break;
            }
            attempted++;
            try {
                String entryUrl = entry.getLink();
                String entryTitle = entry.getTitle() != null ? entry.getTitle() : "entry";
                // 优先取 content，其次 description；都为空则退回链接抓取
                String text = null;
                if (entry.getContents() != null && !entry.getContents().isEmpty()) {
                    text = entry.getContents().get(0).getValue();
                } else if (entry.getDescription() != null) {
                    text = entry.getDescription().getValue();
                }
                if (text != null && !text.isBlank()) {
                    text = Jsoup.parse(text).text();
                    results.add(crudComponent.createDocFromText(knowledgeBaseId, entryTitle + ".md",
                        entryUrl != null ? entryUrl : feedUrl, "RSS",
                        text.getBytes(StandardCharsets.UTF_8)));
                } else if (entryUrl != null && !entryUrl.isBlank()) {
                    results.add(importSingleUrl(knowledgeBaseId, entryUrl, entryTitle));
                }
            } catch (Exception e) {
                log.error("[ypbin-ai] RSS entry 导入失败: title={}", entry.getTitle(), e);
            }
        }
        return results;
    }

    /**
     * 响应状态校验：已禁止跟随重定向，3xx 视为应改用直达地址；4xx/5xx 直接失败。
     *
     * @param response 响应
     * @param url      目标地址（用于提示）
     */
    static void ensureSuccessful(Connection.Response response, String url) {
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            throw new BusinessException(
                "抓取返回重定向（HTTP " + status + "）" + REDIRECT_REJECT_HINT + "：" + url);
        }
        if (status >= 400) {
            throw new BusinessException("抓取失败（HTTP " + status + "）：" + url);
        }
    }
}
