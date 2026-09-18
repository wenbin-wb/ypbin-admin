/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysTrackEvent;
import cn.ypbin.admin.system.model.query.TrackEventQuery;
import cn.ypbin.admin.system.model.resp.TrackEventResp;
import cn.ypbin.admin.system.model.resp.TrackAppCountResp;
import cn.ypbin.admin.system.model.resp.TrackOverviewResp;
import cn.ypbin.admin.system.model.resp.TrackTopEventResp;
import cn.ypbin.admin.system.model.resp.TrackTrendResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 埋点事件查询服务（只读）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
public interface TrackEventService extends BaseService<SysTrackEvent> {

    /**
     * 分页查询事件明细。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<TrackEventResp> pageEvents(TrackEventQuery query);

    /**
     * 事件趋势（按服务端接收时间聚合，缺数据的日期补零）。
     *
     * @param days 天数（1..90）
     * @return 连续日期的计数序列
     */
    List<TrackTrendResp> eventTrend(int days);

    /**
     * 概览统计。
     *
     * @return 概览计数
     */
    TrackOverviewResp overview();

    /**
     * 事件码排行。
     *
     * @param days  统计天数（1..90）
     * @param limit 返回条数（1..50）
     * @return 事件码与次数（不含描述，描述由前端按事件目录映射）
     */
    List<TrackTopEventResp> topEvents(int days, int limit);

    /**
     * 应用维度分布。
     *
     * @param days 统计天数（1..90）
     * @return 应用与次数
     */
    List<TrackAppCountResp> appDistribution(int days);

    /**
     * 导出事件明细（走非分页查询，避免分页上限截断）。
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    void exportEvents(TrackEventQuery query, HttpServletResponse response);
}
