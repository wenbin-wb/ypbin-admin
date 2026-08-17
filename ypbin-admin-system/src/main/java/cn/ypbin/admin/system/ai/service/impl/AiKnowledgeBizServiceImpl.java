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
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.system.ai.mapper.AiKnowledgeBaseMapper;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
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
    public PageResult<AiDocumentVO> pageDocuments(Long knowledgeBaseId, PageQuery query) {
        Page<AiDocument> page = documentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()),
            new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(AiDocument::getCreateTime));
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

    // ------------------------------------------------------------------ 问答 & 检索

    @Override
    public String query(Long knowledgeBaseId, String question) {
        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService == null) {
            throw new BusinessException("AI 对话服务未配置，请在【AI 配置】中添加对话模型");
        }
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
        int k = topK > 0 && topK <= 20 ? topK : 5;
        return ragService.search(String.valueOf(knowledgeBaseId), question, k)
            .stream().map(this::toSearchHit).toList();
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
        List<String> kbIds = knowledgeBaseIds.stream().map(String::valueOf).toList();
        return ragService.searchMultiple(kbIds, question, topKPerKb, 10)
            .stream().map(this::toSearchHit).toList();
    }

    @Override
    public List<Map<String, Object>> searchRerankTest(Long knowledgeBaseId, String question,
            int topK) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            throw new BusinessException("RAG 服务未配置，请在【AI 配置】中添加向量化模型");
        }
        requireKb(knowledgeBaseId);
        return ragService.searchWithRerank(String.valueOf(knowledgeBaseId), question, topK)
            .stream().map(this::toSearchHit).toList();
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

    // ------------------------------------------------------------------ 内部工具

    private Map<String, Object> toSearchHit(Document doc) {
        Map<String, Object> item = new HashMap<>();
        item.put("content", doc.getText());
        item.put("metadata", doc.getMetadata());
        item.put("source", doc.getMetadata().get("source"));
        return item;
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
