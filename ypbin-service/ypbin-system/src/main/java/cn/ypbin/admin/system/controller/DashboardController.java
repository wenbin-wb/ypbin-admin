/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.model.resp.LogResp;
import cn.ypbin.admin.system.model.resp.LogTrendResp;
import cn.ypbin.admin.system.service.DashboardService;
import cn.ypbin.admin.system.service.SysLogService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.security.platform.PlatformAccess;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘统计接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Validated
@PlatformAccess
public class DashboardController {

    private final DashboardService dashboardService;
    private final SysLogService logService;

    /**
     * 系统概览计数：用户 / 角色 / 部门 / 菜单 / 在线用户 / 操作日志。
     */
    @GetMapping("/stats")
    @SaCheckPermission("system:dashboard:view")
    public R<Map<String, Object>> stats() {
        return R.ok(dashboardService.stats());
    }

    /**
     * 最新动态：最近若干条操作日志。
     *
     * @param limit 条数，默认 10
     */
    @GetMapping("/latest-logs")
    @SaCheckPermission("system:dashboard:view")
    public R<List<LogResp>> latestLogs(
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return R.ok(logService.latestLogs(limit));
    }

    /**
     * 操作日志趋势：近若干天按天聚合。
     *
     * @param days 天数，默认 7
     */
    @GetMapping("/log-trend")
    @SaCheckPermission("system:dashboard:view")
    public R<List<LogTrendResp>> logTrend(
        @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return R.ok(logService.logTrend(days));
    }
}
