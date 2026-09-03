/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import cn.ypbin.admin.modules.system.entity.SysLog;
import cn.ypbin.admin.modules.system.model.query.LogQuery;
import cn.ypbin.admin.modules.system.model.resp.LogResp;
import cn.ypbin.admin.modules.system.model.resp.LogTrendResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 系统日志服务（只读）。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysLogService extends BaseService<SysLog> {

    /**
     * 分页查询日志。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<LogResp> pageLogs(LogQuery query);

    /**
     * 查询最近若干条操作日志（用于仪表盘最新动态）。
     *
     * @param limit 条数
     * @return 按操作时间倒序的日志列表
     */
    List<LogResp> latestLogs(int limit);

    /**
     * 查询近若干天的操作日志按天趋势（无日志的日期补零）。
     *
     * @param days 天数
     * @return 每天一条 {date, count}，按日期升序，长度恒为 days
     */
    List<LogTrendResp> logTrend(int days);

    /**
     * 导出操作日志到 Excel。
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    void exportLogs(LogQuery query, HttpServletResponse response);
}
