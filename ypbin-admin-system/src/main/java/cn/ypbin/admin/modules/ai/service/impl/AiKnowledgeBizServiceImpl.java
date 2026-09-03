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

import cn.ypbin.admin.modules.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.modules.ai.model.req.AiDocumentImportReq;
import cn.ypbin.admin.modules.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.modules.ai.model.req.AiKnowledgeBaseUpdateReq;
import cn.ypbin.admin.modules.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.modules.ai.model.resp.KbQueryResult;
import cn.ypbin.admin.modules.ai.service.AiKnowledgeBizService;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库业务实现（编排层）。
 *
 * <p>实现 {@link AiKnowledgeBizService} 接口，按职责委托给三个单一职责组件：
 * {@link AiKnowledgeCrudComponent}（知识库 CRUD 与文档管理）、
 * {@link AiKnowledgeImportComponent}（URL/Sitemap/RSS 导入）、
 * {@link AiKnowledgeSearchComponent}（问答与检索测试）。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeBizServiceImpl implements AiKnowledgeBizService {

    private final AiKnowledgeCrudComponent crudComponent;
    private final AiKnowledgeImportComponent importComponent;
    private final AiKnowledgeSearchComponent searchComponent;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeBase createKnowledgeBase(AiKnowledgeBaseSaveReq req) {
        return crudComponent.createKnowledgeBase(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBase(Long id, AiKnowledgeBaseUpdateReq req) {
        crudComponent.updateKnowledgeBase(id, req);
    }

    @Override
    public List<AiKnowledgeBase> listKnowledgeBases() {
        return crudComponent.listKnowledgeBases();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        crudComponent.deleteKnowledgeBase(id);
    }

    @Override
    public AiDocumentVO uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        return crudComponent.uploadDocument(knowledgeBaseId, file);
    }

    @Override
    public List<AiDocumentVO> batchUploadDocuments(Long knowledgeBaseId, MultipartFile[] files) {
        return crudComponent.batchUploadDocuments(knowledgeBaseId, files);
    }

    @Override
    public PageResult<AiDocumentVO> pageDocuments(Long knowledgeBaseId, PageQuery query,
            String keyword) {
        return crudComponent.pageDocuments(knowledgeBaseId, query, keyword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long knowledgeBaseId, Long docId) {
        crudComponent.deleteDocument(knowledgeBaseId, docId);
    }

    @Override
    public void retryVectorize(Long knowledgeBaseId, Long docId) {
        crudComponent.retryVectorize(knowledgeBaseId, docId);
    }

    @Override
    public List<AiDocumentVO> importFromUrl(Long knowledgeBaseId, AiDocumentImportReq req) {
        return importComponent.importFromUrl(knowledgeBaseId, req);
    }

    @Override
    public String query(Long knowledgeBaseId, String question) {
        return searchComponent.query(knowledgeBaseId, question);
    }

    @Override
    public List<Map<String, Object>> searchTest(Long knowledgeBaseId, String question, int topK) {
        return searchComponent.searchTest(knowledgeBaseId, question, topK);
    }

    @Override
    public List<Map<String, Object>> searchMultipleTest(List<Long> knowledgeBaseIds,
            String question, int topKPerKb) {
        return searchComponent.searchMultipleTest(knowledgeBaseIds, question, topKPerKb);
    }

    @Override
    public List<Map<String, Object>> searchRerankTest(Long knowledgeBaseId, String question,
            int topK) {
        return searchComponent.searchRerankTest(knowledgeBaseId, question, topK);
    }

    @Override
    public KbQueryResult queryWithSources(Long knowledgeBaseId, String question) {
        return searchComponent.queryWithSources(knowledgeBaseId, question);
    }

    @Override
    public String getDocumentContent(Long knowledgeBaseId, Long docId) {
        return crudComponent.getDocumentContent(knowledgeBaseId, docId);
    }

    @Override
    public List<Map<String, Object>> listDocumentChunks(Long knowledgeBaseId, Long docId) {
        return crudComponent.listDocumentChunks(knowledgeBaseId, docId);
    }
}
