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
import cn.ypbin.admin.system.ai.entity.AiDocument;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.system.ai.service.AiKnowledgeBizService;
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

    @PostMapping
    @SaCheckPermission("ai:knowledge:create")
    public R<AiKnowledgeBase> createKnowledgeBase(
            @Valid @RequestBody AiKnowledgeBaseSaveReq req) {
        return ok(knowledgeBizService.createKnowledgeBase(req));
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

    /** 上传文档（PDF / Markdown / TXT），异步向量化 */
    @PostMapping("/{id}/documents")
    @SaCheckPermission("ai:document:upload")
    public R<AiDocument> uploadDocument(
            @PathVariable Long id,
            @RequestParam MultipartFile file) {
        return ok(knowledgeBizService.uploadDocument(id, file));
    }

    @GetMapping("/{id}/documents")
    @SaCheckPermission("ai:knowledge:list")
    public R<PageResult<AiDocument>> pageDocuments(
            @PathVariable Long id, PageQuery query) {
        return ok(knowledgeBizService.pageDocuments(id, query));
    }

    @DeleteMapping("/{id}/documents/{docId}")
    @SaCheckPermission("ai:document:delete")
    public R<Void> deleteDocument(
            @PathVariable Long id, @PathVariable Long docId) {
        knowledgeBizService.deleteDocument(id, docId);
        return ok();
    }

    /** 测试问答（非流式，用于知识库页面的"测试"入口） */
    @PostMapping("/{id}/query")
    @SaCheckPermission("ai:knowledge:list")
    public R<String> query(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ok(knowledgeBizService.query(id, body.get("question")));
    }
}
