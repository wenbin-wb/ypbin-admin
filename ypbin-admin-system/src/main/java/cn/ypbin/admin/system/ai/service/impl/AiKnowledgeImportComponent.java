/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service.impl;

import cn.ypbin.admin.system.ai.model.req.AiDocumentImportReq;
import cn.ypbin.admin.system.ai.model.resp.AiDocumentVO;
import cn.ypbin.starter.core.exception.BusinessException;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * @author wenbin
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class AiKnowledgeImportComponent {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeImportComponent.class);

    /** 网络抓取 UA，标识为知识库抓取机器人 */
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ypbin-knowledge-bot/1.0)";

    /** 抓取超时（毫秒） */
    private static final int FETCH_TIMEOUT_MS = 15_000;

    /** Sitemap 单次导入上限 */
    private static final int SITEMAP_MAX_URLS = 100;

    private final AiKnowledgeCrudComponent crudComponent;

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

    /** 抓取单个网页，提取正文后落库并异步向量化，返回文档 VO */
    private AiDocumentVO importSingleUrl(Long knowledgeBaseId, String url, String customTitle) {
        String content;
        String title;
        try {
            var htmlDoc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(FETCH_TIMEOUT_MS)
                .get();
            title = (customTitle != null && !customTitle.isBlank())
                ? customTitle : htmlDoc.title();
            if (title.isBlank()) {
                title = url;
            }
            // 提取正文：优先 article/main/[role=main]，退回 body
            Element main = htmlDoc.selectFirst("article,main,[role=main]");
            content = (main != null ? main : htmlDoc.body()).text();
        } catch (Exception e) {
            throw new BusinessException("抓取 URL 失败：" + e.getMessage());
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
            var xml = Jsoup.connect(sitemapUrl)
                .userAgent(USER_AGENT)
                .timeout(FETCH_TIMEOUT_MS)
                .ignoreContentType(true)
                .get();
            // 优先 urlset 的 loc；若为 sitemapindex，则递归取出子 sitemap 的 url
            Elements locs = xml.select("urlset > url > loc");
            if (locs.isEmpty()) {
                Elements indexLocs = xml.select("sitemapindex > sitemap > loc");
                for (Element sub : indexLocs) {
                    if (urlsSizeGuard(locs)) {
                        break;
                    }
                    try {
                        var subXml = Jsoup.connect(sub.text())
                            .userAgent(USER_AGENT)
                            .timeout(FETCH_TIMEOUT_MS)
                            .ignoreContentType(true)
                            .get();
                        locs.addAll(subXml.select("urlset > url > loc"));
                    } catch (Exception e) {
                        log.warn("[ypbin-ai] sitemap 子文件解析失败: url={} err={}",
                            sub.text(), e.getMessage());
                    }
                }
            }
            urls = locs.stream().map(Element::text).limit(limit).toList();
        } catch (Exception e) {
            throw new BusinessException("解析 Sitemap 失败：" + e.getMessage());
        }
        if (urls.isEmpty()) {
            throw new BusinessException("Sitemap 中未找到有效 URL");
        }
        List<AiDocumentVO> results = new ArrayList<>();
        for (String u : urls) {
            try {
                results.add(importSingleUrl(knowledgeBaseId, u, null));
            } catch (Exception e) {
                log.warn("[ypbin-ai] Sitemap 导入跳过 URL 失败: url={} err={}", u,
                    e.getMessage());
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
            URL url = URI.create(feedUrl).toURL();
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(url));
            entries = feed.getEntries() != null ? feed.getEntries() : List.of();
        } catch (Exception e) {
            throw new BusinessException("解析 RSS/Atom 失败：" + e.getMessage());
        }
        if (entries.isEmpty()) {
            throw new BusinessException("RSS 中没有文章条目");
        }
        List<AiDocumentVO> results = new ArrayList<>();
        for (SyndEntry entry : entries) {
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
                log.warn("[ypbin-ai] RSS entry 导入失败: title={} err={}",
                    entry.getTitle(), e.getMessage());
            }
        }
        return results;
    }
}
