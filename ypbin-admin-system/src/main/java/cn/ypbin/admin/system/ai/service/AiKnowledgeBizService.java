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
import cn.ypbin.admin.system.ai.model.req.AiDocumentImportReq;
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

    /**
     * 批量上传文档并逐一异步向量化。
     * 单文件失败不影响其它文件；返回成功创建的文档 VO 列表。
     */
    List<AiDocumentVO> batchUploadDocuments(Long knowledgeBaseId, MultipartFile[] files);

    /** 知识库文档分页列表（返回 VO，不暴露内部路径）；keyword 非空时按文件名模糊过滤 */
    PageResult<AiDocumentVO> pageDocuments(Long knowledgeBaseId, PageQuery query,
            String keyword);

    /** 删除文档（同时清理向量数据） */
    void deleteDocument(Long knowledgeBaseId, Long docId);

    /**
     * 重试向量化：读回上传时落盘的原文，重置状态后异步重新向量化。
     * 仅对 file_path 非空的文档可用；原文缺失时抛出业务异常。
     */
    void retryVectorize(Long knowledgeBaseId, Long docId);

    /**
     * 从 URL / Sitemap / RSS 导入文档（异步向量化）。
     *
     * <p>sourceType=URL 时导入单页；SITEMAP 按 sitemap.xml 批量导入（受 maxUrls 限制）；
     * RSS 将订阅源每篇文章导入为一个文档。抓取失败的单条目标会被跳过并记录日志，
     * 不会中断整体导入。
     *
     * @param knowledgeBaseId 目标知识库 ID
     * @param req             导入请求（sourceType/url/maxUrls/customTitle）
     * @return 已创建文档的 VO 列表（批量导入时每条成功 URL 一条）
     */
    List<AiDocumentVO> importFromUrl(Long knowledgeBaseId, AiDocumentImportReq req);

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

    /**
     * 文档全量分块列表（按分块序号升序），供分块可视化与检索诊断。
     * 仅就绪（向量化成功）的文档有分块数据。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param docId           文档 ID
     * @return 分块列表（chunkIndex/content/charCount）
     */
    List<Map<String, Object>> listDocumentChunks(Long knowledgeBaseId, Long docId);
}
