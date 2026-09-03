/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.service.impl;

import cn.ypbin.admin.modules.ai.entity.AiQueryLog;
import cn.ypbin.admin.modules.ai.mapper.AiQueryLogMapper;
import cn.ypbin.admin.modules.ai.model.resp.KbQueryResult;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.core.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 知识库问答与检索测试组件。
 *
 * <p>承载知识库问答、带溯源问答、单库/多库检索测试与关键词重排测试，
 * 从 {@link AiKnowledgeBizServiceImpl} 拆分，保持单一职责。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class AiKnowledgeSearchComponent {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeSearchComponent.class);

    /** 非流式问答最大阻塞时长；超时时直接失败，不挂起请求线程 */
    private static final Duration QUERY_BLOCK_TIMEOUT = Duration.ofSeconds(60);

    /** 检索 topK 上限（防止异常入参打爆向量库） */
    private static final int MAX_TOP_K = 20;

    private final AiQueryLogMapper queryLogMapper;
    private final AiKnowledgeCrudComponent crudComponent;
    private final ObjectProvider<AiRagService> ragServiceProvider;
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

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

    public KbQueryResult queryWithSources(Long knowledgeBaseId, String question) {
        AiRagService ragService = requireRag();
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

    public List<Map<String, Object>> searchTest(Long knowledgeBaseId, String question, int topK) {
        AiRagService ragService = requireRag();
        requireKb(knowledgeBaseId);
        recordQuery(knowledgeBaseId, question, "SEARCH");
        int k = topK > 0 && topK <= MAX_TOP_K ? topK : 5;
        return execSearch(() -> ragService.search(String.valueOf(knowledgeBaseId), question, k)
            .stream().map(doc -> toSearchHit(doc, question)).toList());
    }

    public List<Map<String, Object>> searchMultipleTest(List<Long> knowledgeBaseIds,
            String question, int topKPerKb) {
        AiRagService ragService = requireRag();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        recordQuery(knowledgeBaseIds.get(0), question, "MULTIPLE");
        List<String> kbIds = knowledgeBaseIds.stream().map(String::valueOf).toList();
        return execSearch(() -> ragService.searchMultiple(kbIds, question, topKPerKb, 10)
            .stream().map(doc -> toSearchHit(doc, question)).toList());
    }

    public List<Map<String, Object>> searchRerankTest(Long knowledgeBaseId, String question,
            int topK) {
        AiRagService ragService = requireRag();
        requireKb(knowledgeBaseId);
        recordQuery(knowledgeBaseId, question, "RERANK");
        return execSearch(() -> ragService.searchWithRerank(String.valueOf(knowledgeBaseId), question, topK)
            .stream().map(doc -> toSearchHit(doc, question)).toList());
    }

    private AiRagService requireRag() {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            throw new BusinessException("RAG 服务未配置，请在【AI 配置】中添加向量化模型");
        }
        return ragService;
    }

    private void requireKb(Long knowledgeBaseId) {
        crudComponent.requireKb(knowledgeBaseId);
    }

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
            AiQueryLog logEntry = new AiQueryLog();
            logEntry.setTenantId(crudComponent.currentTenantId());
            logEntry.setKnowledgeBaseId(knowledgeBaseId);
            logEntry.setQuery(query.trim());
            logEntry.setSource(source);
            logEntry.setCreateTime(LocalDateTime.now());
            queryLogMapper.insert(logEntry);
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
}
