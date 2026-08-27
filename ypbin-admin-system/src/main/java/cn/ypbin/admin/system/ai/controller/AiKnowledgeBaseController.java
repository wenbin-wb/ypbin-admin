/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.model.req.AiDocumentImportReq;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseUpdateReq;
import cn.ypbin.admin.system.ai.model.req.AiShareSettingReq;
import cn.ypbin.admin.system.ai.model.req.KbQueryReq;
import cn.ypbin.admin.system.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.system.ai.model.resp.KbQueryResult;
import cn.ypbin.admin.system.ai.service.AiKnowledgeBizService;
import cn.ypbin.admin.system.ai.service.AiShareService;
import cn.ypbin.admin.system.ai.service.AiWidgetService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
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
public class AiKnowledgeBaseController extends BaseController {

    private final AiKnowledgeBizService knowledgeBizService;
    private final AiWidgetService widgetService;
    private final AiShareService shareService;

    @PostMapping
    @SaCheckPermission("ai:knowledge:create")
    public R<AiKnowledgeBase> createKnowledgeBase(
            @Valid @RequestBody AiKnowledgeBaseSaveReq req) {
        return ok(knowledgeBizService.createKnowledgeBase(req));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("ai:knowledge:create")
    public R<Void> updateKnowledgeBase(
            @PathVariable Long id,
            @Valid @RequestBody AiKnowledgeBaseUpdateReq req) {
        knowledgeBizService.updateKnowledgeBase(id, req);
        return ok();
    }

    @GetMapping
    @SaCheckPermission("ai:knowledge:list")
    public R<List<AiKnowledgeBase>> listKnowledgeBases() {
        return ok(knowledgeBizService.listKnowledgeBases());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:knowledge:delete")
    public R<Void> deleteKnowledgeBase(@PathVariable Long id) {
        knowledgeBizService.deleteKnowledgeBase(id);
        return ok();
    }

    /** 上传文档（PDF / Markdown / TXT），异步向量化，返回 VO（不含本地路径） */
    @PostMapping("/{id}/documents")
    @SaCheckPermission("ai:document:upload")
    public R<AiDocumentVO> uploadDocument(
            @PathVariable Long id,
            @RequestParam MultipartFile file) {
        return ok(knowledgeBizService.uploadDocument(id, file));
    }

    @GetMapping("/{id}/documents")
    @SaCheckPermission("ai:knowledge:list")
    public R<PageResult<AiDocumentVO>> pageDocuments(
            @PathVariable Long id, PageQuery query,
            @RequestParam(required = false) String keyword) {
        return ok(knowledgeBizService.pageDocuments(id, query, keyword));
    }

    @DeleteMapping("/{id}/documents/{docId}")
    @SaCheckPermission("ai:document:delete")
    public R<Void> deleteDocument(
            @PathVariable Long id, @PathVariable Long docId) {
        knowledgeBizService.deleteDocument(id, docId);
        return ok();
    }

    /** 批量上传文档（最多 20 个），逐一异步向量化，返回成功创建的文档 VO 列表 */
    @PostMapping("/{id}/documents/batch")
    @SaCheckPermission("ai:document:upload")
    public R<List<AiDocumentVO>> batchUploadDocuments(
            @PathVariable Long id,
            @RequestParam MultipartFile[] files) {
        return ok(knowledgeBizService.batchUploadDocuments(id, files));
    }

    /**
     * 从 URL / Sitemap / RSS 导入文档（异步向量化）。
     *
     * <ul>
     *   <li>URL — 单页抓取，sourceType=URL</li>
     *   <li>SITEMAP — 按 sitemap.xml 批量导入，sourceType=SITEMAP，maxUrls 控制上限</li>
     *   <li>RSS — 订阅源，sourceType=RSS</li>
     * </ul>
     */
    @PostMapping("/{id}/import-url")
    @SaCheckPermission("ai:document:upload")
    public R<List<AiDocumentVO>> importFromUrl(
            @PathVariable Long id,
            @Valid @RequestBody AiDocumentImportReq req) {
        return ok(knowledgeBizService.importFromUrl(id, req));
    }

    /** 重试向量化：对失败文档重新解析与入库 */
    @PostMapping("/{id}/documents/{docId}/retry")
    @SaCheckPermission("ai:document:upload")
    public R<Void> retryVectorize(
            @PathVariable Long id, @PathVariable Long docId) {
        knowledgeBizService.retryVectorize(id, docId);
        return ok();
    }

    /** 知识库问答（非流式） */
    @PostMapping("/{id}/query")
    @SaCheckPermission("ai:knowledge:list")
    public R<String> query(
            @PathVariable Long id,
            @Valid @RequestBody KbQueryReq req) {
        return ok(knowledgeBizService.query(id, req.getQuestion()));
    }

    /** 检索测试：返回召回片段，供调优检索策略 */
    @PostMapping("/{id}/search-test")
    @SaCheckPermission("ai:knowledge:list")
    public R<List<Map<String, Object>>> searchTest(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        int topK = body.get("topK") == null ? 5 : Integer.parseInt(String.valueOf(body.get("topK")));
        return ok(knowledgeBizService.searchTest(id, question, topK));
    }

    /** 关键词重叠重排测试 */
    @PostMapping("/{id}/search-rerank-test")
    @SaCheckPermission("ai:knowledge:list")
    public R<List<Map<String, Object>>> searchRerankTest(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        int topK = body.get("topK") == null ? 5 : Integer.parseInt(String.valueOf(body.get("topK")));
        return ok(knowledgeBizService.searchRerankTest(id, question, topK));
    }

    /** 多知识库联合检索测试（跨库 RRF 合并） */
    @PostMapping("/search-multiple-test")
    @SaCheckPermission("ai:knowledge:list")
    public R<List<Map<String, Object>>> searchMultipleTest(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<?> rawIds = (List<?>) body.get("knowledgeBaseIds");
        List<Long> kbIds = rawIds == null ? List.of()
            : rawIds.stream().map(x -> Long.valueOf(String.valueOf(x))).toList();
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        int topKPerKb = body.get("topKPerKb") == null
            ? 5 : Integer.parseInt(String.valueOf(body.get("topKPerKb")));
        return ok(knowledgeBizService.searchMultipleTest(kbIds, question, topKPerKb));
    }

    /** 带溯源的问答（答案 + 召回片段列表） */
    @PostMapping("/{id}/query-with-sources")
    @SaCheckPermission("ai:knowledge:list")
    public R<KbQueryResult> queryWithSources(
            @PathVariable Long id,
            @Valid @RequestBody KbQueryReq req) {
        return ok(knowledgeBizService.queryWithSources(id, req.getQuestion()));
    }

    /**
     * 启用/停用知识库网页挂件。
     *
     * @param enabled true 启用（生成新令牌，返回令牌），false 停用（清除令牌）
     */
    @PutMapping("/{id}/widget")
    @SaCheckPermission("ai:knowledge:create")
    public R<String> setWidgetEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        return ok(widgetService.setWidgetEnabled(id, enabled));
    }

    /**
     * 保存知识库公开分享设置。
     *
     * <p>启用时返回分享令牌（已有令牌保留，轮换需先关闭再开启），可配置有效期与访问密码；
     * 停用时清除全部分享配置。</p>
     */
    @PutMapping("/{id}/share")
    @SaCheckPermission("ai:knowledge:create")
    public R<String> setShareSetting(
            @PathVariable Long id,
            @Valid @RequestBody AiShareSettingReq req) {
        return ok(shareService.setShareSetting(id, req));
    }

    /** 读取文档原文内容（Wiki 阅读页渲染用） */
    @GetMapping("/{id}/documents/{docId}/content")
    @SaCheckPermission("ai:knowledge:list")
    public R<String> getDocumentContent(
            @PathVariable Long id, @PathVariable Long docId) {
        return ok(knowledgeBizService.getDocumentContent(id, docId));
    }

    /** 文档全量分块列表（分块可视化） */
    @GetMapping("/{id}/documents/{docId}/chunks")
    @SaCheckPermission("ai:knowledge:list")
    public R<List<Map<String, Object>>> listDocumentChunks(
            @PathVariable Long id, @PathVariable Long docId) {
        return ok(knowledgeBizService.listDocumentChunks(id, docId));
    }
}
