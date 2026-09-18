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
import cn.ypbin.admin.system.model.query.TrackEventQuery;
import cn.ypbin.admin.system.model.resp.TrackEventResp;
import cn.ypbin.admin.system.model.resp.TrackAppCountResp;
import cn.ypbin.admin.system.model.resp.TrackFunnelResp;
import cn.ypbin.admin.system.model.resp.TrackOverviewResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionResp;
import cn.ypbin.admin.system.model.resp.TrackTopEventResp;
import cn.ypbin.admin.system.model.resp.TrackTrendResp;
import cn.ypbin.admin.system.service.TrackAnalysisService;
import cn.ypbin.admin.system.service.TrackEventService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.security.platform.PlatformAccess;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 埋点事件查询接口。
 *
 * <p>只读接口，全部在 {@link PlatformAccess} 之下（平台用户才可访问）：埋点数据包含用户行为与客户端信息，
 * 不应下放到租户侧。</p>
 *
 * <p>注意：方法上的 {@code @SaCheckPermission} 是权限码声明；服务内的注解鉴权拦截器是否生效取决于部署配置
 * （见 {@code ypbin.security.interceptor}），因此本类同时依赖 {@code @PlatformAccess} 做兜底。</p>
 *
 * <p>采集端点（{@code POST /tracking/ingest}）由 starter 的埋点模块提供，与本控制器路径不冲突。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
@PlatformAccess
public class TrackEventController {

    private final TrackEventService trackEventService;

    private final TrackAnalysisService trackAnalysisService;

    /**
     * 概览统计。
     *
     * @return 概览计数
     */
    @GetMapping("/overview")
    @SaCheckPermission("system:track:list")
    public R<TrackOverviewResp> overview() {
        return R.ok(trackEventService.overview());
    }

    /**
     * 事件趋势。
     *
     * @param days 天数（1..90，默认 7）
     * @return 连续日期的计数序列
     */
    @GetMapping("/events/trend")
    @SaCheckPermission("system:track:list")
    public R<List<TrackTrendResp>> trend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(trackEventService.eventTrend(days));
    }

    /**
     * 事件码排行（分析页 Top 事件）。
     *
     * @param days  统计天数（1..90，默认 7）
     * @param limit 返回条数（1..50，默认 10）
     * @return 事件码与次数；中文描述由前端按事件目录映射
     */
    @GetMapping("/events/top")
    @SaCheckPermission("system:track:list")
    public R<List<TrackTopEventResp>> topEvents(@RequestParam(defaultValue = "7") int days,
                                                @RequestParam(defaultValue = "10") int limit) {
        return R.ok(trackEventService.topEvents(days, limit));
    }

    /**
     * 应用维度分布。
     *
     * @param days 统计天数（1..90，默认 7）
     * @return 应用与次数
     */
    @GetMapping("/apps/distribution")
    @SaCheckPermission("system:track:list")
    public R<List<TrackAppCountResp>> appDistribution(@RequestParam(defaultValue = "7") int days) {
        return R.ok(trackEventService.appDistribution(days));
    }

    /**
     * 事件明细分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/events/list")
    @SaCheckPermission("system:track:list")
    public R<PageResult<TrackEventResp>> list(@Valid TrackEventQuery query) {
        return R.ok(trackEventService.pageEvents(query));
    }

    /**
     * 导出事件明细。
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    @GetMapping("/events/export")
    @SaCheckPermission("system:track:list")
    public void export(@Valid TrackEventQuery query, HttpServletResponse response) {
        trackEventService.exportEvents(query, response);
    }

    /**
     * 会话级漏斗分析。
     *
     * @param steps 逗号分隔的事件码（2..8 个）
     * @param days  统计天数（1..90，默认 7）
     * @return 每步的会话数与相对首步的转化率，以及「被截断的会话数」（大于 0 时各步数字只是下限）
     */
    @GetMapping("/funnel")
    @SaCheckPermission("system:track:list")
    public R<TrackFunnelResp> funnel(@RequestParam String steps,
                                     @RequestParam(defaultValue = "7") int days) {
        return R.ok(trackAnalysisService.funnel(steps, days));
    }

    /**
     * 用户留存矩阵与摘要。
     *
     * @param days 分析天数（1..90，默认 7）
     * @return 完整矩阵（最长 7×7）+ D1/D7/D30 摘要；仅覆盖登录用户
     */
    @GetMapping("/retention")
    @SaCheckPermission("system:track:list")
    public R<TrackRetentionResp> retention(@RequestParam(defaultValue = "7") int days) {
        return R.ok(trackAnalysisService.retention(days));
    }
}
