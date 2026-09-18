/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 留存查询结果：完整矩阵 + 摘要列。
 *
 * <p><strong>口径</strong>：以用户当日首次出现为 D0，D0 取「全部历史里最早出现的那天」——
 * 窗口开始前就出现过的用户不会在窗口内被当成新用户重复计入。</p>
 *
 * <p><strong>匿名事件不参与</strong>（无 {@code user_id} 无法判定同一用户）：本结果只覆盖登录用户，
 * 展示时必须写明，否则会被误读为整体留存。</p>
 *
 * <p><strong>矩阵是固定 {@code matrixDays} × {@code matrixDays} 的完整网格</strong>：
 * 行的首次出现日都取「已经过完整个观察窗口」的那几天，因此矩阵内不存在「尚未到第 N 日」的空单元，
 * 代价是需要回溯 {@code 2 * matrixDays - 1} 天的历史数据。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackRetentionResp {

    /** 请求的分析天数（决定摘要的统计窗口宽度与可用的偏移日上限） */
    private Integer days;

    /** 矩阵网格边长（行数 = 列数 = 本值） */
    private Integer matrixDays;

    /** 矩阵行的首次出现日（升序，yyyy-MM-dd），长度等于 matrixDays */
    private List<String> cohortDates;

    /** 矩阵列的偏移日（0..matrixDays-1，升序） */
    private List<Integer> dayOffsets;

    /** 矩阵行 */
    private List<TrackRetentionRowResp> rows;

    /** 摘要列（D1/D7/D30 中在本次窗口内可观察的那些） */
    private List<TrackRetentionSummaryResp> summary;
}
