/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.ai.model.req.AiDocumentImportReq;
import cn.ypbin.admin.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.ai.model.req.AiKnowledgeBaseUpdateReq;
import cn.ypbin.admin.ai.model.req.AiShareSettingReq;
import cn.ypbin.admin.ai.model.req.KbMultiSearchTestReq;
import cn.ypbin.admin.ai.model.req.KbQueryReq;
import cn.ypbin.admin.ai.model.req.KbSearchTestReq;
import cn.ypbin.admin.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.ai.model.resp.KbQueryResult;
import cn.ypbin.admin.ai.service.AiKnowledgeBizService;
import cn.ypbin.admin.ai.service.AiShareService;
import cn.ypbin.admin.ai.service.AiWidgetService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import cn.ypbin.starter.log.annotation.Log;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理接口。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/ai/knowledge-bases")
@RequiredArgsConstructor
public class AiKnowledgeBaseController {

    private final AiKnowledgeBizService knowledgeBizService;
    private final AiWidgetService widgetService;
    private final AiShareService shareService;

    @Log(value = "创建知识库", module = "AI知识库")
    @Idempotent
    @PostMapping
    @SaCheckPermission("ai:knowledge:create")
    public R<AiKnowledgeBase> createKnowledgeBase(
            @Valid @RequestBody AiKnowledgeBaseSaveReq req) {
        return R.ok(knowledgeBizService.createKnowledgeBase(req));
    }

    @Log(value = "更新知识库", module = "AI知识库")
    @Idempotent
    @PutMapping("/{id}")
    @SaCheckPermission("ai:knowledge:create")
    public R<Void> updateKnowledgeBase(
            @PathVariable Long id,
            @Valid @RequestBody AiKnowledgeBaseUpdateReq req) {
        knowledgeBizService.updateKnowledgeBase(id, req);
        return R.ok();
    }

    @GetMapping
    @SaCheckPermission("ai:knowledge:list")
    public R<List<AiKnowledgeBase>> listKnowledgeBases() {
        return R.ok(knowledgeBizService.listKnowledgeBases());
    }

    @Log(value = "删除知识库", module = "AI知识库")
    @Idempotent
    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:knowledge:delete")
    public R<Void> deleteKnowledgeBase(@PathVariable Long id) {
        knowledgeBizService.deleteKnowledgeBase(id);
        return R.ok();
    }

    /** 上传文档（PDF / Markdown / TXT），异步向量化，返回 VO（不含本地路径） */
    @Log(value = "上传文档", module = "AI知识库")
    @Idempotent
    @PostMapping("/{id}/documents")
    @SaCheckPermission("ai:document:upload")
    public R<AiDocumentVO> uploadDocument(
            @PathVariable Long id,
            @RequestParam MultipartFile file) {
        return R.ok(knowledgeBizService.uploadDocument(id, file));
    }

    @GetMapping("/{id}/documents")
    @SaCheckPermission("ai:knowledge:list")
    public R<PageResult<AiDocumentVO>> pageDocuments(
            @PathVariable Long id, PageQuery query,
            @RequestParam(required = false) String keyword) {
        return R.ok(knowledgeBizService.pageDocuments(id, query, keyword));
    }

    @Log(value = "删除文档", module = "AI知识库")
    @Idempotent
    @DeleteMapping("/{id}/documents/{docId}")
    @SaCheckPermission("ai:document:delete")
    public R<Void> deleteDocument(
            @PathVariable Long id, @PathVariable Long docId) {
        knowledgeBizService.deleteDocument(id, docId);
        return R.ok();
    }

    /** 批量上传文档（最多 20 个），逐一异步向量化，返回成功创建的文档 VO 列表 */
    @Log(value = "批量上传文档", module = "AI知识库")
    @Idempotent
    @PostMapping("/{id}/documents/batch")
    @SaCheckPermission("ai:document:upload")
    public R<List<AiDocumentVO>> batchUploadDocuments(
            @PathVariable Long id,
            @RequestParam MultipartFile[] files) {
        return R.ok(knowledgeBizService.batchUploadDocuments(id, files));
    }

    /**
     * 从 URL / Sitemap / RSS 导入文档（异步向量化）。
     */
    @Log(value = "导入网络文档", module = "AI知识库")
    @Idempotent
    @PostMapping("/{id}/import-url")
    @SaCheckPermission("ai:document:upload")
    public R<List<AiDocumentVO>> importFromUrl(
            @PathVariable Long id,
            @Valid @RequestBody AiDocumentImportReq req) {
        return R.ok(knowledgeBizService.importFromUrl(id, req));
    }

    /** 重试向量化：对失败文档重新解析与入库 */
    @Log(value = "重试向量化", module = "AI知识库")
    @Idempotent
    @PostMapping("/{id}/documents/{docId}/retry")
    @SaCheckPermission("ai:document:upload")
    public R<Void> retryVectorize(
            @PathVariable Long id, @PathVariable Long docId) {
        knowledgeBizService.retryVectorize(id, docId);
        return R.ok();
    }

    /** 知识库问答（非流式） */
    @PostMapping("/{id}/query")
    @SaCheckPermission("ai:knowledge:list")
    public R<String> query(
            @PathVariable Long id,
            @Valid @RequestBody KbQueryReq req) {
        return R.ok(knowledgeBizService.query(id, req.getQuestion()));
    }

    /** 检索测试：返回召回片段，供调优检索策略 */
    @PostMapping("/{id}/search-test")
    @SaCheckPermission("ai:knowledge:list")
    public R<List<Map<String, Object>>> searchTest(
            @PathVariable Long id,
            @Valid @RequestBody KbSearchTestReq req) {
        return R.ok(knowledgeBizService.searchTest(id, req.getQuestion(), req.getTopK()));
    }

    /** 关键词重叠重排测试 */
    @PostMapping("/{id}/search-rerank-test")
    @SaCheckPermission("ai:knowledge:list")
    public R<List<Map<String, Object>>> searchRerankTest(
            @PathVariable Long id,
            @Valid @RequestBody KbSearchTestReq req) {
        return R.ok(knowledgeBizService.searchRerankTest(id, req.getQuestion(), req.getTopK()));
    }

    /** 多知识库联合检索测试（跨库 RRF 合并） */
    @PostMapping("/search-multiple-test")
    @SaCheckPermission("ai:knowledge:list")
    public R<List<Map<String, Object>>> searchMultipleTest(
            @Valid @RequestBody KbMultiSearchTestReq req) {
        return R.ok(knowledgeBizService.searchMultipleTest(req.getKnowledgeBaseIds(), req.getQuestion(), req.getTopKPerKb()));
    }

    /** 带溯源的问答（答案 + 召回片段列表） */
    @PostMapping("/{id}/query-with-sources")
    @SaCheckPermission("ai:knowledge:list")
    public R<KbQueryResult> queryWithSources(
            @PathVariable Long id,
            @Valid @RequestBody KbQueryReq req) {
        return R.ok(knowledgeBizService.queryWithSources(id, req.getQuestion()));
    }

    /**
     * 启用/停用知识库网页挂件。
     */
    @Log(value = "设置挂件状态", module = "AI知识库")
    @Idempotent
    @PutMapping("/{id}/widget")
    @SaCheckPermission("ai:knowledge:create")
    public R<String> setWidgetEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        return R.ok(widgetService.setWidgetEnabled(id, enabled));
    }

    /**
     * 保存知识库公开分享设置。
     */
    @Log(value = "保存分享设置", module = "AI知识库")
    @Idempotent
    @PutMapping("/{id}/share")
    @SaCheckPermission("ai:knowledge:create")
    public R<String> setShareSetting(
            @PathVariable Long id,
            @Valid @RequestBody AiShareSettingReq req) {
        return R.ok(shareService.setShareSetting(id, req));
    }

    /** 读取文档原文内容（Wiki 阅读页渲染用） */
    @GetMapping("/{id}/documents/{docId}/content")
    @SaCheckPermission("ai:knowledge:list")
    public R<String> getDocumentContent(
            @PathVariable Long id, @PathVariable Long docId) {
        return R.ok(knowledgeBizService.getDocumentContent(id, docId));
    }

    /** 文档全量分块列表（分块可视化） */
    @GetMapping("/{id}/documents/{docId}/chunks")
    @SaCheckPermission("ai:knowledge:list")
    public R<List<Map<String, Object>>> listDocumentChunks(
            @PathVariable Long id, @PathVariable Long docId) {
        return R.ok(knowledgeBizService.listDocumentChunks(id, docId));
    }
}
