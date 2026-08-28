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

import cn.ypbin.admin.system.ai.entity.AiDocument;
import cn.ypbin.admin.system.ai.entity.AiDocumentChunk;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.entity.AiQueryLog;
import cn.ypbin.admin.system.ai.mapper.AiDocumentChunkMapper;
import cn.ypbin.admin.system.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.system.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.system.ai.mapper.AiQueryLogMapper;
import cn.ypbin.admin.system.ai.model.req.AiDocumentImportReq;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseUpdateReq;
import cn.ypbin.admin.system.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.system.ai.model.resp.KbQueryResult;
import cn.ypbin.admin.system.ai.service.AiDocumentVectorizer;
import cn.ypbin.admin.system.ai.service.AiKnowledgeBizService;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库业务实现。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeBizServiceImpl implements AiKnowledgeBizService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeBizServiceImpl.class);

    /** 非流式问答最大阻塞时长；超时时直接失败，不挂起请求线程 */
    private static final Duration QUERY_BLOCK_TIMEOUT = Duration.ofSeconds(60);

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiQueryLogMapper queryLogMapper;
    private final AiDocumentVectorizer documentVectorizer;
    private final ObjectProvider<AiRagService> ragServiceProvider;
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    // ------------------------------------------------------------------ 知识库 CRUD

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeBase createKnowledgeBase(AiKnowledgeBaseSaveReq req) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setTenantId(currentTenantId());
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setIcon(req.getIcon());
        kb.setRemark(req.getRemark());
        kb.setDocCount(0);
        kbMapper.insert(kb);
        return kb;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBase(Long id, AiKnowledgeBaseUpdateReq req) {
        AiKnowledgeBase kb = requireKb(id);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setIcon(req.getIcon());
        kb.setRemark(req.getRemark());
        kb.setUpdateTime(LocalDateTime.now());
        kbMapper.updateById(kb);
    }

    @Override
    public List<AiKnowledgeBase> listKnowledgeBases() {
        return kbMapper.selectList(
            new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getTenantId, currentTenantId())
                .orderByDesc(AiKnowledgeBase::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        requireKb(id);
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.delete(String.valueOf(id));
        }
        kbMapper.deleteById(id);
        documentMapper.delete(new LambdaQueryWrapper<AiDocument>()
            .eq(AiDocument::getKnowledgeBaseId, id));
        chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
            .eq(AiDocumentChunk::getKnowledgeBaseId, id));
    }

    // ------------------------------------------------------------------ 文档管理

    @Override
    public AiDocumentVO uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        requireKb(knowledgeBaseId);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";

        AiDocument doc = new AiDocument();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setTenantId(currentTenantId());
        doc.setFilename(filename);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(0);
        doc.setSourceType("UPLOAD");
        doc.setCreateTime(LocalDateTime.now());
        documentMapper.insert(doc);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            markDocFailed(doc.getId(), "文件读取失败：" + e.getMessage());
            return AiDocumentVO.from(doc);
        }

        String filePath = persistOriginalFile(knowledgeBaseId, doc.getId(), filename, bytes);
        if (filePath != null) {
            AiDocument pathUpdate = new AiDocument();
            pathUpdate.setId(doc.getId());
            pathUpdate.setFilePath(filePath);
            documentMapper.updateById(pathUpdate);
        }

        documentVectorizer.vectorizeAsync(
            doc.getId(), knowledgeBaseId, doc.getTenantId(), filename, bytes);
        return AiDocumentVO.from(doc);
    }

    @Override
    public List<AiDocumentVO> batchUploadDocuments(Long knowledgeBaseId,
            MultipartFile[] files) {
        requireKb(knowledgeBaseId);
        if (files == null || files.length == 0) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (files.length > 20) {
            throw new BusinessException("单次最多上传 20 个文件");
        }
        List<AiDocumentVO> results = new ArrayList<>();
        int failed = 0;
        for (MultipartFile file : files) {
            try {
                results.add(uploadDocument(knowledgeBaseId, file));
            } catch (Exception e) {
                failed++;
                log.warn("[ypbin-ai] 批量上传单个文件失败: filename={} err={}",
                    file.getOriginalFilename(), e.getMessage());
            }
        }
        if (failed > 0 && results.isEmpty()) {
            throw new BusinessException("全部文件上传失败，请检查文件格式与内容");
        }
        return results;
    }

    @Override
    public PageResult<AiDocumentVO> pageDocuments(Long knowledgeBaseId, PageQuery query,
            String keyword) {
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<AiDocument>()
            .eq(AiDocument::getKnowledgeBaseId, knowledgeBaseId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AiDocument::getFilename, keyword.trim());
        }
        wrapper.orderByDesc(AiDocument::getCreateTime);
        Page<AiDocument> page = documentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        List<AiDocumentVO> vos = page.getRecords().stream()
            .map(AiDocumentVO::from).toList();
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long knowledgeBaseId, Long docId) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.deleteDocument(String.valueOf(knowledgeBaseId), String.valueOf(docId));
        }
        documentMapper.deleteById(docId);
        chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
            .eq(AiDocumentChunk::getDocumentId, docId));
        kbMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeBase>()
            .eq(AiKnowledgeBase::getId, knowledgeBaseId)
            .gt(AiKnowledgeBase::getDocCount, 0)
            .setSql("doc_count = doc_count - 1"));
    }

    @Override
    public void retryVectorize(Long knowledgeBaseId, Long docId) {
        AiDocument doc = requireDoc(knowledgeBaseId, docId);
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new BusinessException("该文档未保存原文，无法重试，请删除后重新上传");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException("原文件不存在（可能已被清理），请删除后重新上传");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            AiDocument update = new AiDocument();
            update.setId(docId);
            update.setStatus(0);
            update.setErrorMsg(null);
            update.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(update);
            documentVectorizer.vectorizeAsync(
                docId, knowledgeBaseId, doc.getTenantId(), doc.getFilename(), bytes);
        } catch (IOException e) {
            throw new BusinessException("读取原文件失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ URL 导入

    @Override
    public List<AiDocumentVO> importFromUrl(Long knowledgeBaseId, AiDocumentImportReq req) {
        requireKb(knowledgeBaseId);
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
            org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; ypbin-knowledge-bot/1.0)")
                .timeout(15_000)
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
        return createDocFromText(knowledgeBaseId, title + ".md", url, "URL",
            content.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析 Sitemap（支持 sitemapindex），逐个 URL 抓取导入，最多 maxUrls 条 */
    private List<AiDocumentVO> importSitemap(Long knowledgeBaseId, String sitemapUrl,
            Integer maxUrls) {
        int limit = (maxUrls == null || maxUrls < 1) ? 10 : Math.min(maxUrls, 100);
        List<String> urls;
        try {
            org.jsoup.nodes.Document xml = Jsoup.connect(sitemapUrl)
                .userAgent("Mozilla/5.0 (compatible; ypbin-knowledge-bot/1.0)")
                .timeout(15_000)
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
                        org.jsoup.nodes.Document subXml = Jsoup.connect(sub.text())
                            .userAgent("Mozilla/5.0 (compatible; ypbin-knowledge-bot/1.0)")
                            .timeout(15_000)
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
        return locs.size() >= 100;
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
                    results.add(createDocFromText(knowledgeBaseId, entryTitle + ".md",
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

    /** 将文本内容落库并异步向量化，返回 VO */
    private AiDocumentVO createDocFromText(Long knowledgeBaseId, String filename,
            String sourceUrl, String sourceType, byte[] bytes) {
        AiDocument doc = new AiDocument();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setTenantId(currentTenantId());
        doc.setFilename(filename);
        doc.setFileSize((long) bytes.length);
        doc.setChunkCount(0);
        doc.setStatus(0);
        doc.setSourceType(sourceType);
        doc.setSourceUrl(sourceUrl);
        doc.setCreateTime(LocalDateTime.now());
        documentMapper.insert(doc);

        String filePath = persistOriginalFile(knowledgeBaseId, doc.getId(), filename, bytes);
        if (filePath != null) {
            AiDocument pathUpdate = new AiDocument();
            pathUpdate.setId(doc.getId());
            pathUpdate.setFilePath(filePath);
            documentMapper.updateById(pathUpdate);
        }

        documentVectorizer.vectorizeAsync(
            doc.getId(), knowledgeBaseId, doc.getTenantId(), filename, bytes);
        return AiDocumentVO.from(doc);
    }

    // ------------------------------------------------------------------ 问答 & 检索

    @Override
    public String query(Long knowledgeBaseId, String question) {
        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService == null) {
            throw new BusinessException("AI 对话服务未配置，请在【AI 配置】中添加对话模型");
        }
        recordQuery(knowledgeBaseId, question, "QUERY");
        List<String> tokens = aiChatService.chatWithKnowledge(
                "kb-query-" + knowledgeBaseId, question, String.valueOf(knowledgeBaseId))
            .collectList()
            .block(QUERY_BLOCK_TIMEOUT);
        return tokens == null ? "" : String.join("", tokens);
    }

    @Override
    public KbQueryResult queryWithSources(Long knowledgeBaseId, String question) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            throw new BusinessException("RAG 服务未配置，请在【AI 配置】中添加向量化模型");
        }
        requireKb(knowledgeBaseId);
        List<Document> docs = ragService.searchWithRerank(
            String.valueOf(knowledgeBaseId), question, 5);
        List<KbQueryResult.SourceFragment> sources = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            KbQueryResult.SourceFragment frag = new KbQueryResult.SourceFragment();
            frag.setSource(String.valueOf(doc.getMetadata().getOrDefault("source", "")));
            frag.setContent(doc.getText());
            frag.setMetadata(doc.getMetadata());
            sources.add(frag);
        }
        String answer = query(knowledgeBaseId, question);
        KbQueryResult result = new KbQueryResult();
        result.setAnswer(answer);
        result.setSources(sources);
        return result;
    }

    @Override
    public List<Map<String, Object>> searchTest(Long knowledgeBaseId, String question, int topK) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            throw new BusinessException("RAG 服务未配置，请在【AI 配置】中添加向量化模型");
        }
        requireKb(knowledgeBaseId);
        recordQuery(knowledgeBaseId, question, "SEARCH");
        int k = topK > 0 && topK <= 20 ? topK : 5;
        return execSearch(() -> ragService.search(String.valueOf(knowledgeBaseId), question, k)
            .stream().map(doc -> toSearchHit(doc, question)).toList());
    }

    @Override
    public List<Map<String, Object>> searchMultipleTest(List<Long> knowledgeBaseIds,
            String question, int topKPerKb) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            throw new BusinessException("RAG 服务未配置，请在【AI 配置】中添加向量化模型");
        }
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        recordQuery(knowledgeBaseIds.get(0), question, "MULTIPLE");
        List<String> kbIds = knowledgeBaseIds.stream().map(String::valueOf).toList();
        return execSearch(() -> ragService.searchMultiple(kbIds, question, topKPerKb, 10)
            .stream().map(doc -> toSearchHit(doc, question)).toList());
    }

    @Override
    public List<Map<String, Object>> searchRerankTest(Long knowledgeBaseId, String question,
            int topK) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            throw new BusinessException("RAG 服务未配置，请在【AI 配置】中添加向量化模型");
        }
        requireKb(knowledgeBaseId);
        recordQuery(knowledgeBaseId, question, "RERANK");
        return execSearch(() -> ragService.searchWithRerank(String.valueOf(knowledgeBaseId), question, topK)
            .stream().map(doc -> toSearchHit(doc, question)).toList());
    }

    @Override
    public String getDocumentContent(Long knowledgeBaseId, Long docId) {
        AiDocument doc = requireDoc(knowledgeBaseId, docId);
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new BusinessException("文档未落盘，无法读取内容");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException("文档文件不存在（可能已被清理）");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[ypbin-ai] 读取文档内容失败: docId={}", docId, e);
            throw new BusinessException("读取文档内容失败：" + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listDocumentChunks(Long knowledgeBaseId, Long docId) {
        requireDoc(knowledgeBaseId, docId);
        List<AiDocumentChunk> chunks = chunkMapper.selectList(
            new LambdaQueryWrapper<AiDocumentChunk>()
                .eq(AiDocumentChunk::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentChunk::getDocumentId, docId)
                .orderByAsc(AiDocumentChunk::getChunkIndex));
        return chunks.stream()
            .map(c -> Map.<String, Object>of(
                "chunkIndex", c.getChunkIndex(),
                "content", c.getContent() == null ? "" : c.getContent(),
                "charCount", c.getCharCount() != null ? c.getCharCount() : 0))
            .toList();
    }

    // ------------------------------------------------------------------ 内部工具

    /**
     * 记录一次检索/问答日志（统计搜索热词与趋势）。
     *
     * <p>统计旁路：写入失败不影响主流程（检索/问答仍正常返回），但必须记录日志暴露问题，
     * 不允许静默吞掉。</p>
     */
    private void recordQuery(Long knowledgeBaseId, String query, String source) {
        if (query == null || query.isBlank() || knowledgeBaseId == null) {
            return;
        }
        try {
            AiQueryLog log = new AiQueryLog();
            log.setTenantId(currentTenantId());
            log.setKnowledgeBaseId(knowledgeBaseId);
            log.setQuery(query.trim());
            log.setSource(source);
            log.setCreateTime(LocalDateTime.now());
            queryLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("[ypbin-ai] 记录检索日志失败: kbId={} query={} err={}",
                knowledgeBaseId, query, e.getMessage());
        }
    }

    /**
     * 执行一次向量检索并做友好错误转换：向量化模型未配置时底层懒加载向量库
     * 会抛 {@link IllegalStateException}，这里转为业务异常（HTTP 200 + R.code 409），
     * 避免直接 500 且返回可读提示。
     */
    private <T> T execSearch(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalStateException e) {
            log.warn("[ypbin-ai] 检索执行失败: {}", e.getMessage());
            throw new BusinessException("AI 模型未配置，请在【AI 配置】中添加向量化模型");
        }
    }

    /**
     * 检索片段组装为响应结构，并附加启发式评估字段（关键词相关度）。
     *
     * @param doc   检索命中的分块
     * @param query 检索问题（用于计算关键词相关度）
     */
    private Map<String, Object> toSearchHit(Document doc, String query) {
        Map<String, Object> item = new HashMap<>();
        Map<String, Object> meta = doc.getMetadata() == null ? Map.of() : doc.getMetadata();
        String text = doc.getText() == null ? "" : doc.getText();
        item.put("content", text);
        item.put("metadata", meta);
        item.put("source", meta.get("source"));
        item.put("docId", meta.get("documentId"));
        item.put("docName", meta.get("filename") != null ? meta.get("filename") : meta.get("source"));
        item.put("charCount", text.length());
        Map<String, Object> relevance = keywordRelevance(query, text);
        item.put("score", relevance.get("score"));
        item.put("hitKeywords", relevance.get("hitKeywords"));
        item.put("maxHitLen", relevance.get("maxHitLen"));
        return item;
    }

    /**
     * 轻量关键词相关度评估（0-100）：query 分词后与片段文本的关键词命中比 + 最长连续命中占比。
     *
     * <p>说明：这是检索测试器用于可视化召回质量的启发式评估分，不代表向量相似度
     * （embedding 相似度由向量库内部计算，当前不对外暴露）。</p>
     */
    private static Map<String, Object> keywordRelevance(String query, String text) {
        if (query == null || text == null || query.isBlank() || text.isBlank()) {
            return Map.of("score", 0, "hitKeywords", List.of(), "maxHitLen", 0);
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        String lowerText = text.toLowerCase(Locale.ROOT);
        List<String> tokens = splitQueryTokens(lowerQuery);
        List<String> hits = new ArrayList<>();
        int maxHitLen = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (lowerText.contains(token)) {
                hits.add(token);
                if (token.length() > maxHitLen) {
                    maxHitLen = token.length();
                }
            }
        }
        if (tokens.isEmpty()) {
            return Map.of("score", 0, "hitKeywords", hits, "maxHitLen", maxHitLen);
        }
        double hitRatio = (double) hits.size() / tokens.size();
        double lenRatio = Math.min(1.0, (double) maxHitLen / lowerQuery.length());
        int score = (int) Math.round(100 * (0.6 * hitRatio + 0.4 * lenRatio));
        return Map.of(
            "score", Math.max(0, Math.min(100, score)),
            "hitKeywords", hits,
            "maxHitLen", maxHitLen);
    }

    /**
     * 查询分词：英文/数字按非字母数字切分；连续中文按 2 字滑动窗口切分
     * （避免整句作为单一关键词在片段中难以精确命中），同时保留整词用于最长命中评估。
     */
    private static List<String> splitQueryTokens(String query) {
        List<String> tokens = new ArrayList<>();
        for (String part : query.split("[^\\p{L}\\p{N}]+")) {
            if (part.isBlank()) {
                continue;
            }
            if (part.matches(".*[\\u4e00-\\u9fa5].*")) {
                for (int i = 0; i + 2 <= part.length(); i++) {
                    String gram = part.substring(i, i + 2);
                    if (!tokens.contains(gram)) {
                        tokens.add(gram);
                    }
                }
                tokens.add(part);
            } else {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private String persistOriginalFile(Long knowledgeBaseId, Long docId,
            String filename, byte[] bytes) {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"),
                "data", "ai-files", String.valueOf(knowledgeBaseId));
            Files.createDirectories(dir);
            String safeName = (filename == null || filename.isBlank())
                ? "document" : filename.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path target = dir.resolve(docId + "-" + safeName);
            Files.write(target, bytes);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("[ypbin-ai] 文档原文落盘失败: docId={}", docId, e);
            return null;
        }
    }

    private void markDocFailed(Long docId, String errorMsg) {
        AiDocument update = new AiDocument();
        update.setId(docId);
        update.setStatus(2);
        update.setErrorMsg(errorMsg != null && errorMsg.length() > 490
            ? errorMsg.substring(0, 490) : errorMsg);
        update.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(update);
    }

    private AiKnowledgeBase requireKb(Long id) {
        AiKnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        return kb;
    }

    private AiDocument requireDoc(Long knowledgeBaseId, Long docId) {
        AiDocument doc = documentMapper.selectById(docId);
        if (doc == null || !Objects.equals(doc.getKnowledgeBaseId(), knowledgeBaseId)) {
            throw new BusinessException("文档不存在");
        }
        return doc;
    }

    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}
