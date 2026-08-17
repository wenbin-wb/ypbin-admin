/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service;

import cn.ypbin.admin.system.ai.entity.AiDocument;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.system.ai.model.resp.AiConversationResp;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库业务接口。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiKnowledgeBizService {

    /** 新建知识库 */
    AiKnowledgeBase createKnowledgeBase(AiKnowledgeBaseSaveReq req);

    /** 知识库列表 */
    List<AiKnowledgeBase> listKnowledgeBases();

    /** 删除知识库（同时删除向量数据） */
    void deleteKnowledgeBase(Long id);

    /**
     * 上传文档并异步向量化（分为两步：先落库状态"处理中"，再 @Async 向量化）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param file            上传的文件（PDF / Markdown / TXT）
     */
    AiDocument uploadDocument(Long knowledgeBaseId, MultipartFile file);

    /** 知识库文档分页列表 */
    PageResult<AiDocument> pageDocuments(Long knowledgeBaseId, PageQuery query);

    /** 删除文档（同时删除向量数据） */
    void deleteDocument(Long knowledgeBaseId, Long docId);

    /**
     * 重试向量化：读回上传时落盘的原文，将文档状态重置为"处理中"后异步重新向量化。
     * 仅对存在原文（file_path 非空）的文档可用。
     */
    void retryVectorize(Long knowledgeBaseId, Long docId);

    /** 对知识库直接提问（非流式，用于"测试问答"入口） */
    String query(Long knowledgeBaseId, String question);

    /** 检索测试：返回召回的文档片段列表（含原文与元数据），供检索测试器展示调优 */
    List<Map<String, Object>> searchTest(Long knowledgeBaseId, String question, int topK);

    /** 多知识库联合检索测试：跨库召回合并（RRF），供前端比对单库/多库效果 */
    List<Map<String, Object>> searchMultipleTest(List<Long> knowledgeBaseIds, String question,
            int topKPerKb);

    /** 关键词重叠重排测试：对召回片段做精排后返回，供前端观察重排效果 */
    List<Map<String, Object>> searchRerankTest(Long knowledgeBaseId, String question, int topK);
}