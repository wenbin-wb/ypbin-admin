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
import cn.ypbin.admin.system.ai.service.AiStatsService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 统计看板接口（平台级，仅授权角色可见）。
 *
 * @author wenbin
 * @since 2026-08-18
 */
@RestController
@RequestMapping("/ai/stats")
@RequiredArgsConstructor
public class AiStatsController extends BaseController {

    private final AiStatsService statsService;

    /** 概览统计：知识库数/文档总数/问答次数/检索次数/Token 总量 */
    @GetMapping("/summary")
    @SaCheckPermission("ai:usage:view")
    public R<Map<String, Object>> summary() {
        return ok(statsService.summary());
    }

    /** 近 N 天问答/检索/Token 趋势（默认 30 天） */
    @GetMapping("/daily")
    @SaCheckPermission("ai:usage:view")
    public R<List<Map<String, Object>>> daily(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return ok(statsService.daily(days));
    }

    /** 搜索热词 Top N（默认 10） */
    @GetMapping("/hot-queries")
    @SaCheckPermission("ai:usage:view")
    public R<List<Map<String, Object>>> hotQueries(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ok(statsService.hotQueries(limit));
    }

    /** 各知识库文档数分布 */
    @GetMapping("/kb-docs")
    @SaCheckPermission("ai:usage:view")
    public R<List<Map<String, Object>>> kbDocDistribution() {
        return ok(statsService.kbDocDistribution());
    }
}
