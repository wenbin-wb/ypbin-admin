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
import cn.ypbin.admin.system.ai.service.AiUsageService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Token 用量统计接口（平台级，仅授权角色可见）。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/ai/usage")
@RequiredArgsConstructor
public class AiUsageController extends BaseController {

    private final AiUsageService usageService;

    /**
     * 按天聚合的 Token 用量（折线图数据）。
     *
     * @param startDate 开始日期（默认近 30 天）
     * @param endDate   结束日期
     * @return 每天的 totalTokens 合计
     */
    @GetMapping("/daily")
    @SaCheckPermission("ai:usage:view")
    public R<List<Map<String, Object>>> dailyUsage(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return ok(usageService.dailyUsage(startDate, endDate));
    }

    /**
     * 按模型聚合的 Token 用量（饼图数据）。
     */
    @GetMapping("/by-model")
    @SaCheckPermission("ai:usage:view")
    public R<List<Map<String, Object>>> byModel() {
        return ok(usageService.byModel());
    }

    /**
     * 用量概览：总对话数、总 Token 数、平均响应耗时。
     */
    @GetMapping("/summary")
    @SaCheckPermission("ai:usage:view")
    public R<Map<String, Object>> summary() {
        return ok(usageService.summary());
    }
}
