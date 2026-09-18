/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.support;

import cn.ypbin.admin.system.model.resp.TrackRetentionCellResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionPointResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionRowResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionSummaryResp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 留存矩阵与摘要的组装。
 *
 * <p>聚合 SQL 只返回「确实有回头用户」的点，而矩阵需要完整网格（缺的格子补 0）。
 * 补零不能省：把缺失当成「不存在的格子」，前端会把相邻两列直接连线，把「某天没人回来」
 * 误读成「留存平滑过渡」。</p>
 *
 * <h3>窗口口径（本类唯一的事实源）</h3>
 * <ul>
 *   <li>{@code maxOffset = min(days, 30)}：可用偏移日上限，决定摘要能出到 D1/D7/D30 中的哪几列；</li>
 *   <li><strong>摘要窗口</strong>：截至 {@code today - maxOffset - 1} 的最近 {@code days} 天，
 *       因此窗口内每个首次出现日 + {@code maxOffset} 的目标日<strong>最晚是昨天（已过完）</strong>，
 *       不会出现半截数据；</li>
 *   <li><strong>矩阵窗口</strong>：固定 {@code matrixDays = min(days, 7)} 行，
 *       取截至 {@code today - matrixDays} 的最近 {@code matrixDays} 天——
 *       即矩阵是完整的 {@code matrixDays × matrixDays} 网格，没有「尚未到第 N 日」的空单元，
 *       代价是需要回溯 {@code 2 * matrixDays - 1} 天（days≥7 时为 13 天）的历史数据。</li>
 * </ul>
 *
 * <p><strong>为什么是 {@code today - maxOffset - 1} 而不是 {@code today - maxOffset}</strong>：
 * 摘要把整个窗口的同一偏移日<strong>求和</strong>成一列。若取到 {@code today - maxOffset}，
 * 最新那个首次出现日的目标日恰好是<strong>今天</strong>——今天还没过完，该 cohort 的第 N 日
 * 留存人数必然偏少，这一列会被系统性地<strong>低估</strong>（且看不出任何异常）。
 * 减 1 保证窗口内每个 cohort 的目标日都已完整结束；矩阵侧同理，
 * 其最大目标日是 {@code today - 1}，两侧口径一致。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackRetentionMatrixBuilder {

    /** 矩阵网格边长上限 */
    public static final int MAX_MATRIX_DAYS = 7;

    /** 摘要可出的最大偏移日（D30 是业务上最常见的最后一列） */
    public static final int MAX_SUMMARY_OFFSET = 30;

    /** 摘要列固定取这几个偏移日，超出窗口的自动剔除 */
    private static final List<Integer> SUMMARY_OFFSETS = List.of(1, 7, 30);

    /** 留存率保留小数位 */
    private static final int RATE_SCALE = 6;

    /** 留存率舍入方式 */
    private static final RoundingMode RATE_ROUNDING = RoundingMode.HALF_UP;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TrackRetentionMatrixBuilder() {
    }

    /**
     * 计算聚合查询需要的起始首次出现日（含）。
     *
     * <p>SQL 按「首次出现日 ∈ [起始日, 今天]」与「偏移日 ≤ {@link #maxOffset}」取数，
     * 这一个区间要同时覆盖摘要窗口与矩阵窗口，故取下式（取两者中更早的那个；已证明恒成立）：
     * 摘要窗口起点 {@code today - (maxOffset + 1) - (days - 1)}，
     * 矩阵窗口起点 {@code today - 2 * matrixDays + 1}。</p>
     *
     * @param days  分析天数（正数）
     * @param today 今天
     * @return 起始首次出现日
     */
    public static LocalDate earliestCohortDate(int days, LocalDate today) {
        requirePositiveDays(days);
        return today.minusDays((long) maxOffset(days) + days);
    }

    /**
     * 可用偏移日上限。
     *
     * @param days 分析天数
     * @return {@code min(days, 30)}
     */
    public static int maxOffset(int days) {
        requirePositiveDays(days);
        return Math.min(days, MAX_SUMMARY_OFFSET);
    }

    /**
     * 矩阵网格边长。
     *
     * @param days 分析天数
     * @return {@code min(days, 7)}
     */
    public static int matrixDays(int days) {
        requirePositiveDays(days);
        return Math.min(days, MAX_MATRIX_DAYS);
    }

    /**
     * 摘要列偏移日。
     *
     * @param days 分析天数
     * @return D1/D7/D30 中不超过 {@link #maxOffset} 的那些（升序）
     */
    public static List<Integer> summaryOffsets(int days) {
        int maxOffset = maxOffset(days);
        return SUMMARY_OFFSETS.stream().filter(offset -> offset <= maxOffset).toList();
    }

    /**
     * 组装留存结果。
     *
     * @param days   分析天数（调用方已校验范围）
     * @param today  今天
     * @param points 聚合点（可为空集合）
     * @return 完整矩阵 + 摘要列
     */
    public static TrackRetentionResp build(int days, LocalDate today, List<TrackRetentionPointResp> points) {
        requirePositiveDays(days);
        Map<String, Long> counts = indexPoints(points);

        int matrixDays = matrixDays(days);
        LocalDate matrixEnd = today.minusDays(matrixDays);
        LocalDate matrixStart = matrixEnd.minusDays(matrixDays - 1L);

        List<String> cohortDates = new ArrayList<>(matrixDays);
        List<Integer> dayOffsets = new ArrayList<>(matrixDays);
        for (int index = 0; index < matrixDays; index++) {
            cohortDates.add(matrixStart.plusDays(index).format(DATE_FORMATTER));
            dayOffsets.add(index);
        }

        List<TrackRetentionRowResp> rows = new ArrayList<>(matrixDays);
        for (int index = 0; index < matrixDays; index++) {
            rows.add(toRow(matrixStart.plusDays(index), dayOffsets, counts));
        }

        TrackRetentionResp resp = new TrackRetentionResp();
        resp.setDays(days);
        resp.setMatrixDays(matrixDays);
        resp.setCohortDates(cohortDates);
        resp.setDayOffsets(dayOffsets);
        resp.setRows(rows);
        resp.setSummary(toSummary(days, today, counts));
        return resp;
    }

    private static TrackRetentionRowResp toRow(LocalDate cohortDate, List<Integer> dayOffsets,
                                               Map<String, Long> counts) {
        String date = cohortDate.format(DATE_FORMATTER);
        long cohortSize = counts.getOrDefault(key(date, 0), 0L);
        List<TrackRetentionCellResp> cells = new ArrayList<>(dayOffsets.size());
        for (Integer dayOffset : dayOffsets) {
            long userCount = counts.getOrDefault(key(date, dayOffset), 0L);
            TrackRetentionCellResp cell = new TrackRetentionCellResp();
            cell.setDayOffset(dayOffset);
            cell.setUserCount(userCount);
            cell.setRetentionRate(toRate(userCount, cohortSize));
            cells.add(cell);
        }
        TrackRetentionRowResp row = new TrackRetentionRowResp();
        row.setCohortDate(date);
        row.setCohortSize(cohortSize);
        row.setCells(cells);
        return row;
    }

    /**
     * 组装摘要列（每个偏移日跨 cohort 求和）。
     *
     * <p>窗口末端取 {@code today - maxOffset - 1}：保证窗口内每个 cohort 的目标日
     * （{@code cohortDate + maxOffset}）最晚是<strong>昨天</strong>，即已完整结束。
     * 若取 {@code today - maxOffset}，最新 cohort 的目标日是今天（半截日），
     * 其留存人数偏少会把整列拉低——这是「看起来正常但系统性偏低」的错误，必须避免。</p>
     *
     * @param days   分析天数
     * @param today  今天
     * @param counts 聚合点索引
     * @return 摘要列（D1/D7/D30 中可用者，升序）
     */
    private static List<TrackRetentionSummaryResp> toSummary(int days, LocalDate today,
                                                            Map<String, Long> counts) {
        int maxOffset = maxOffset(days);
        LocalDate windowEnd = today.minusDays(maxOffset + 1L);
        List<TrackRetentionSummaryResp> summary = new ArrayList<>();
        for (Integer dayOffset : summaryOffsets(days)) {
            long cohortSize = 0L;
            long userCount = 0L;
            for (int index = 0; index < days; index++) {
                String date = windowEnd.minusDays(index).format(DATE_FORMATTER);
                cohortSize += counts.getOrDefault(key(date, 0), 0L);
                userCount += counts.getOrDefault(key(date, dayOffset), 0L);
            }
            TrackRetentionSummaryResp item = new TrackRetentionSummaryResp();
            item.setDayOffset(dayOffset);
            item.setCohortSize(cohortSize);
            item.setUserCount(userCount);
            item.setRetentionRate(toRate(userCount, cohortSize));
            summary.add(item);
        }
        return summary;
    }

    /**
     * 把聚合点索引成 {@code 首次出现日 + 偏移日 -> 用户数}。
     *
     * @param points 聚合点
     * @return 索引（偏移日为 null 的点被忽略：它无法定位到矩阵的某一列）
     */
    private static Map<String, Long> indexPoints(List<TrackRetentionPointResp> points) {
        Map<String, Long> counts = new HashMap<>();
        if (points == null) {
            return counts;
        }
        for (TrackRetentionPointResp point : points) {
            if (point.getCohortDate() == null || point.getDayOffset() == null) {
                continue;
            }
            counts.put(key(point.getCohortDate().format(DATE_FORMATTER), point.getDayOffset()),
                point.getUserCount() == null ? 0L : point.getUserCount());
        }
        return counts;
    }

    private static String key(String cohortDate, int dayOffset) {
        return cohortDate + '#' + dayOffset;
    }

    private static BigDecimal toRate(long count, long total) {
        if (total <= 0) {
            // 该首次出现日没有任何用户：分母缺失，返回 null 表示「不可计算」，不伪造成 0%
            return null;
        }
        return BigDecimal.valueOf(count).divide(BigDecimal.valueOf(total), RATE_SCALE, RATE_ROUNDING);
    }

    private static void requirePositiveDays(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("分析天数必须为正数，当前为 " + days);
        }
    }
}
