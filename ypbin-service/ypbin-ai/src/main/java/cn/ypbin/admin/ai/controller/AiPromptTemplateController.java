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
import cn.ypbin.admin.ai.model.resp.AiPromptTemplateResp;
import cn.ypbin.admin.ai.model.req.AiPromptTemplateSaveReq;
import cn.ypbin.admin.ai.service.AiPromptTemplateService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import cn.ypbin.starter.log.annotation.Log;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板接口。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/ai/prompt-templates")
@RequiredArgsConstructor
public class AiPromptTemplateController {

    private final AiPromptTemplateService promptTemplateService;

    @GetMapping
    @SaCheckPermission("ai:prompt:list")
    public R<List<AiPromptTemplateResp>> list() {
        return R.ok(promptTemplateService.listTemplates());
    }

    @Log(value = "新增提示词模板", module = "Prompt模板")
    @Idempotent
    @PostMapping
    @SaCheckPermission("ai:prompt:create")
    public R<Void> create(@Valid @RequestBody AiPromptTemplateSaveReq req) {
        promptTemplateService.createTemplate(req);
        return R.ok();
    }

    @Log(value = "修改提示词模板", module = "Prompt模板")
    @Idempotent
    @PutMapping("/{id}")
    @SaCheckPermission("ai:prompt:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AiPromptTemplateSaveReq req) {
        promptTemplateService.updateTemplate(id, req);
        return R.ok();
    }

    @Log(value = "删除提示词模板", module = "Prompt模板")
    @Idempotent
    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:prompt:delete")
    public R<Void> delete(@PathVariable Long id) {
        promptTemplateService.deleteTemplate(id);
        return R.ok();
    }

    /** 更新状态（启用/停用）*/
    @Log(value = "更新提示词模板状态", module = "Prompt模板")
    @Idempotent
    @PutMapping("/{id}/status/{status}")
    @SaCheckPermission("ai:prompt:edit")
    public R<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        promptTemplateService.updateStatus(id, status);
        return R.ok();
    }
}
