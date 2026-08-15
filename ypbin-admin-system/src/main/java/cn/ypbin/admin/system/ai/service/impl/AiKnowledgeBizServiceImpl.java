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
import cn.ypbin.admin.system.ai.service.AiKnowledgeBizService;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.ai.rag.DocumentLoader;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final ObjectProvider<AiRagService> ragServiceProvider;
    private final AiChatService aiChatService;

    @Override
    public AiKnowledgeBase createKnowledgeBase(cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseSaveReq req) {
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setTenantId(tenantId);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setRemark(req.getRemark());
        kb.setDocCount(0);
        kbMapper.insert(kb);
        return kb;
    }

    @Override
    public List<AiKnowledgeBase> listKnowledgeBases() {
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);
        return kbMapper.selectList(
            new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getTenantId, tenantId)
                .orderByDesc(AiKnowledgeBase::getCreateTime));
    }

    @Override
    public void deleteKnowledgeBase(Long id) {
        requireKb(id);
        // 删向量数据
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.delete(String.valueOf(id));
        }
        // 逻辑删除知识库和文档记录
        kbMapper.deleteById(id);
        documentMapper.delete(new LambdaQueryWrapper<AiDocument>()
            .eq(AiDocument::getKnowledgeBaseId, id));
    }

    @Override
    public AiDocument uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        requireKb(knowledgeBaseId);
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);

        // 先落库，状态"处理中"
        AiDocument doc = new AiDocument();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setTenantId(tenantId);
        doc.setFilename(file.getOriginalFilename());
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(0);
        doc.setCreateTime(LocalDateTime.now());
        documentMapper.insert(doc);

        // 异步向量化
        vectorizeAsync(doc.getId(), knowledgeBaseId, file);

        return doc;
    }

    @Override
    public PageResult<AiDocument> pageDocuments(Long knowledgeBaseId, PageQuery query) {
        Page<AiDocument> page = documentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()),
            new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(AiDocument::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void deleteDocument(Long knowledgeBaseId, Long docId) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.deleteDocument(String.valueOf(knowledgeBaseId), String.valueOf(docId));
        }
        documentMapper.deleteById(docId);
        // 更新知识库文档计数
        decrementDocCount(knowledgeBaseId);
    }

    @Override
    public String query(Long knowledgeBaseId, String question) {
        return aiChatService.chatWithKnowledge(
            "kb-query-" + knowledgeBaseId, question, String.valueOf(knowledgeBaseId))
            .collectList()
            .block()
            .stream()
            .reduce("", String::concat);
    }

    @Async
    void vectorizeAsync(Long docId, Long knowledgeBaseId, MultipartFile file) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            markDocFailed(docId, "RAG 未启用，请配置 ypbin.ai.rag.enabled=true 及向量库");
            return;
        }
        try {
            List<Document> chunks = parseAndChunk(file, docId, knowledgeBaseId);
            ragService.ingest(String.valueOf(knowledgeBaseId), chunks);
            // 更新文档状态为"就绪"
            AiDocument update = new AiDocument();
            update.setId(docId);
            update.setChunkCount(chunks.size());
            update.setStatus(1);
            update.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(update);
            // 更新知识库文档计数
            incrementDocCount(knowledgeBaseId);
            log.debug("[ypbin-ai] 文档向量化完成: docId={}, chunks={}", docId, chunks.size());
        } catch (Exception e) {
            log.error("[ypbin-ai] 文档向量化失败: docId={}", docId, e);
            markDocFailed(docId, e.getMessage());
        }
    }

    private List<Document> parseAndChunk(MultipartFile file, Long docId, Long knowledgeBaseId)
            throws Exception {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        Map<String, Object> metadata = Map.of(
            "knowledgeBaseId", String.valueOf(knowledgeBaseId),
            "documentId", String.valueOf(docId),
            "filename", filename);
        return DocumentLoader.loadAndChunk(file.getBytes(), filename, metadata);
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

    private void incrementDocCount(Long kbId) {
        AiKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb != null) {
            kb.setDocCount(kb.getDocCount() == null ? 1 : kb.getDocCount() + 1);
            kbMapper.updateById(kb);
        }
    }

    private void decrementDocCount(Long kbId) {
        AiKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb != null && kb.getDocCount() != null && kb.getDocCount() > 0) {
            kb.setDocCount(kb.getDocCount() - 1);
            kbMapper.updateById(kb);
        }
    }

    private AiKnowledgeBase requireKb(Long id) {
        AiKnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        return kb;
    }
}
