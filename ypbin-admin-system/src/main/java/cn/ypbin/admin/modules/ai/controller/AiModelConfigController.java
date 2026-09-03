/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.modules.ai.model.req.AiModelConfigSaveReq;
import cn.ypbin.admin.modules.ai.model.resp.AiModelConfigResp;
import cn.ypbin.admin.modules.ai.service.AiModelConfigService;
import cn.ypbin.admin.modules.system.annotation.PlatformAccess;
import cn.ypbin.starter.core.model.R;
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

/**
 * AI 模型配置管理接口（仅平台管理员可操作）。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/ai/models")
@RequiredArgsConstructor
@PlatformAccess
public class AiModelConfigController {

    private final AiModelConfigService modelConfigService;

    @GetMapping
    @SaCheckPermission("ai:model:list")
    public R<List<AiModelConfigResp>> listModels(
            @RequestParam(value = "modelType", required = false) String modelType) {
        return R.ok(modelConfigService.listModels(modelType));
    }

    @Idempotent
    @PostMapping
    @SaCheckPermission("ai:model:create")
    @Log(value = "新增模型配置", module = "AI 配置")
    public R<Void> createModel(@Valid @RequestBody AiModelConfigSaveReq req) {
        modelConfigService.createModel(req);
        return R.ok();
    }

    @Idempotent
    @PutMapping("/{id}")
    @SaCheckPermission("ai:model:edit")
    @Log(value = "修改模型配置", module = "AI 配置")
    public R<Void> updateModel(
            @PathVariable Long id,
            @Valid @RequestBody AiModelConfigSaveReq req) {
        modelConfigService.updateModel(id, req);
        return R.ok();
    }

    @Idempotent
    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:model:delete")
    @Log(value = "删除模型配置", module = "AI 配置")
    public R<Void> deleteModel(@PathVariable Long id) {
        modelConfigService.deleteModel(id);
        return R.ok();
    }

    /** 设为默认模型 */
    @Idempotent
    @PutMapping("/{id}/default")
    @SaCheckPermission("ai:model:edit")
    @Log(value = "设置默认模型", module = "AI 配置")
    public R<Void> setDefault(@PathVariable Long id) {
        modelConfigService.setDefault(id);
        return R.ok();
    }

    /** 启用/停用模型 */
    @Idempotent
    @PutMapping("/{id}/status/{status}")
    @SaCheckPermission("ai:model:edit")
    @Log(value = "切换模型状态", module = "AI 配置")
    public R<Void> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        modelConfigService.updateStatus(id, status);
        return R.ok();
    }

    /** 复制模型配置 */
    @Idempotent
    @PostMapping("/{id}/duplicate")
    @SaCheckPermission("ai:model:create")
    @Log(value = "复制模型配置", module = "AI 配置")
    public R<Long> duplicate(@PathVariable Long id) {
        return R.ok(modelConfigService.duplicate(id));
    }

    /** 测试连通性，返回响应耗时 ms */
    @PostMapping("/{id}/test")
    @SaCheckPermission("ai:model:list")
    public R<Map<String, Long>> testConnection(@PathVariable Long id) {
        long latencyMs = modelConfigService.testConnection(id);
        return R.ok(Map.of("latencyMs", latencyMs));
    }
}
