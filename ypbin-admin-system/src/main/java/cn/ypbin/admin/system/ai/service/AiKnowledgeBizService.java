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

import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseUpdateReq;
import cn.ypbin.admin.system.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.system.ai.model.resp.KbQueryResult;
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

    /** 编辑知识库（名称、描述、图标、备注） */
    void updateKnowledgeBase(Long id, AiKnowledgeBaseUpdateReq req);

    /** 知识库列表（按创建时间倒序） */
    List<AiKnowledgeBase> listKnowledgeBases();

    /** 删除知识库（同时清理向量数据与文档记录） */
    void deleteKnowledgeBase(Long id);

    /**
     * 上传文档并异步向量化。
     * 先将记录落库（状态"处理中"），再于 @Async 线程向量化。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param file            上传的文件（PDF / Markdown / TXT）
     */
    AiDocumentVO uploadDocument(Long knowledgeBaseId, MultipartFile file);

    /** 知识库文档分页列表（返回 VO，不暴露内部路径） */
    PageResult<AiDocumentVO> pageDocuments(Long knowledgeBaseId, PageQuery query);

    /** 删除文档（同时清理向量数据） */
    void deleteDocument(Long knowledgeBaseId, Long docId);

    /**
     * 重试向量化：读回上传时落盘的原文，重置状态后异步重新向量化。
     * 仅对 file_path 非空的文档可用；原文缺失时抛出业务异常。
     */
    void retryVectorize(Long knowledgeBaseId, Long docId);

    /**
     * 对知识库直接提问（非流式）。
     * AI 模块未配置时抛出业务异常，而非静默返回提示语。
     */
    String query(Long knowledgeBaseId, String question);

    /** 检索测试：返回召回片段列表（含原文与元数据），供检索调优使用 */
    List<Map<String, Object>> searchTest(Long knowledgeBaseId, String question, int topK);

    /** 多知识库联合检索测试（RRF 合并） */
    List<Map<String, Object>> searchMultipleTest(List<Long> knowledgeBaseIds, String question,
            int topKPerKb);

    /** 关键词重叠重排测试：对召回片段做精排后返回 */
    List<Map<String, Object>> searchRerankTest(Long knowledgeBaseId, String question, int topK);

    /**
     * 带溯源的问答：同时返回 AI 答案与召回片段。
     * AI 或 RAG 服务未配置时抛出业务异常。
     */
    KbQueryResult queryWithSources(Long knowledgeBaseId, String question);

    /**
     * 读取文档原文内容（供 Wiki 阅读页渲染 Markdown）。
     * 仅支持落盘（file_path 非空）且文件存在的文档。
     */
    String getDocumentContent(Long knowledgeBaseId, Long docId);
}
