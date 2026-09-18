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

import cn.ypbin.admin.system.model.resp.TrackFunnelResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionResp;

/**
 * 埋点分析服务（漏斗与留存，只读）。
 *
 * <p>与 {@code TrackEventService} 分开：本服务只查三张预聚合表，不碰明细表——
 * 明细表按天增长且带 {@code payload} 大字段，不适合做有序扫描与自连接。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public interface TrackAnalysisService {

    /**
     * 会话级漏斗。
     *
     * <p>口径：同一会话内按时间顺序出现全部步骤，允许中间夹其它事件，命中即前移、不回退；
     * 不做跨会话的用户级漏斗。</p>
     *
     * @param steps 逗号分隔的事件码（2..8 个）
     * @param days  统计天数（1..90）
     * @return 每步的会话数与相对首步的转化率，外加「被截断的会话数」（大于 0 时各步数字只是下限）
     */
    TrackFunnelResp funnel(String steps, int days);

    /**
     * 用户留存矩阵与摘要。
     *
     * <p>口径：以用户当日首次出现为 D0；只覆盖 {@code user_id} 非空的登录用户，
     * 匿名事件不参与（无用户标识无法判定同一用户）。</p>
     *
     * @param days 分析天数（1..90）
     * @return 完整矩阵（最长 7×7）+ D1/D7/D30 摘要
     */
    TrackRetentionResp retention(int days);
}
