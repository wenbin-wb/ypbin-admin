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
import cn.ypbin.admin.ai.model.req.AiModelConfigSaveReq;
import cn.ypbin.admin.ai.model.resp.AiModelConfigResp;
import cn.ypbin.admin.ai.service.AiModelConfigService;
import cn.ypbin.starter.security.platform.PlatformAccess;
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
@RequestMapping("/models")
@RequiredArgsConstructor
@PlatformAccess
public class AiModelConfigController {

    private final AiModelConfigService modelConfigService;

    @GetMapping
    @SaCheckPermission("ai:model:list")
    public R<List<AiModelConfigResp>> listModels(
            @RequestParam(value = "modelType", required = false) String modelType,
            @RequestParam(value = "status", required = false) Integer status) {
        return R.ok(modelConfigService.listModels(modelType, status));
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

    /**
     * 测试模型连通性，返回响应耗时 ms。
     *
     * <p><strong>本端点会消耗外部模型配额，故记录操作日志（{@code @Log}）</strong>：{@code testConnection}
     * 会解密 API Key 并向 {@code baseUrl} 发真实 HTTP 请求，属于会产生对外副作用与费用的敏感动作，
     * 必须能追溯「谁在什么时候测试了哪个模型」。{@code @Log} 默认采集 {@code REQUEST_PARAM}，这里只有
     * 路径变量 {@code id}（模型配置主键），<b>不含密钥</b>——Key 在 service 内解密，不进入请求参数，
     * 因此不会把 API Key 写进 {@code sys_log}。</p>
     *
     * <p>刻意<b>不加</b> {@code @Idempotent}：同一配置重复测试结果一致（幂等语义的读操作），
     * 加防重会误伤「首次超时后立即重试」这类正常排障操作。</p>
     *
     * <p>说明：同模块其余 5 个问答/检索端点（{@code query} / {@code searchTest} /
     * {@code searchMultipleTest} / {@code searchRerankTest} / {@code queryWithSources}）的审计由
     * {@code ai_query_log} 承担（结构化记录 kbId + 提问原文 + 操作人），<b>不挂</b> {@code @Log}——
     * 不要把它们当成遗漏再补注解。</p>
     *
     * @param id 模型配置主键
     * @return 响应耗时（毫秒）
     */
    @Log(value = "模型连通性测试", module = "AI 配置")
    @PostMapping("/{id}/test")
    @SaCheckPermission("ai:model:list")
    public R<Map<String, Long>> testConnection(@PathVariable Long id) {
        long latencyMs = modelConfigService.testConnection(id);
        return R.ok(Map.of("latencyMs", latencyMs));
    }
}
