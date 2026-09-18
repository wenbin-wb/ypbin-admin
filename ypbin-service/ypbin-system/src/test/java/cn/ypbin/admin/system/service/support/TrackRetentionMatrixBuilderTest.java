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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.admin.system.model.resp.TrackRetentionCellResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionPointResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionRowResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionSummaryResp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 留存矩阵组装测试（纯逻辑，不依赖数据库与系统时钟）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackRetentionMatrixBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    @Test
    void shouldBuildCompleteSevenBySevenMatrix() {
        TrackRetentionResp resp = TrackRetentionMatrixBuilder.build(7, TODAY, points());

        assertThat(resp.getDays()).isEqualTo(7);
        assertThat(resp.getMatrixDays()).isEqualTo(7);
        // 7×7 是「7 个首次出现日 × 7 个偏移日」的完整网格：行内不含尚未到第 N 日的空洞
        assertThat(resp.getCohortDates()).containsExactly(
            "2026-09-03", "2026-09-04", "2026-09-05", "2026-09-06",
            "2026-09-07", "2026-09-08", "2026-09-09");
        assertThat(resp.getDayOffsets()).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(resp.getRows()).hasSize(7);
        assertThat(resp.getRows()).allSatisfy(row -> assertThat(row.getCells()).hasSize(7));
    }

    @Test
    void shouldFillMissingCellsWithZero() {
        TrackRetentionResp resp = TrackRetentionMatrixBuilder.build(7, TODAY, points());

        TrackRetentionRowResp first = resp.getRows().get(0);
        assertThat(first.getCohortDate()).isEqualTo("2026-09-03");
        assertThat(first.getCohortSize()).isEqualTo(100L);
        assertThat(first.getCells().get(0).getRetentionRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(first.getCells().get(1).getUserCount()).isEqualTo(40L);
        assertThat(first.getCells().get(1).getRetentionRate()).isEqualByComparingTo(new BigDecimal("0.4"));
        assertThat(first.getCells().get(6).getUserCount()).isEqualTo(10L);
        assertThat(first.getCells().get(6).getRetentionRate()).isEqualByComparingTo(new BigDecimal("0.1"));
        // D2~D5 数据库里没有点，必须补 0 而不是留空：留空会让渲染层把两列直接连线
        assertThat(first.getCells().get(2).getUserCount()).isZero();
        assertThat(first.getCells().get(2).getRetentionRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldAggregateSummaryByCohortSize() {
        TrackRetentionResp resp = TrackRetentionMatrixBuilder.build(7, TODAY, points());

        assertThat(resp.getSummary()).hasSize(2);
        TrackRetentionSummaryResp d1 = resp.getSummary().get(0);
        assertThat(d1.getDayOffset()).isEqualTo(1);
        // 摘要窗口是截至 today-7-1=09-08 的最近 7 天（09-02..09-08），基数按行加权而不是把各行留存率取平均
        assertThat(d1.getCohortSize()).isEqualTo(100L);
        assertThat(d1.getUserCount()).isEqualTo(40L);
        assertThat(d1.getRetentionRate()).isEqualByComparingTo(new BigDecimal("0.4"));

        TrackRetentionSummaryResp d7 = resp.getSummary().get(1);
        assertThat(d7.getDayOffset()).isEqualTo(7);
        assertThat(d7.getCohortSize()).isEqualTo(100L);
        assertThat(d7.getUserCount()).isZero();
        assertThat(d7.getRetentionRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldExcludeCohortWhoseTargetDayIsToday() {
        // 边界：days=7、today=2026-09-16 时，cohort 09-09 的 D7 目标日正好是「今天」——今天尚未过完，
        // 它的 D7 人数必然偏少，计入摘要会把 D7 列系统性拉低（复核已用 jshell 复现）。
        // 同时断言 cohort 09-08 的 D7 目标日是「昨天」（已过完），必须被计入。
        List<TrackRetentionPointResp> boundary = List.of(
            point(LocalDate.of(2026, 9, 8), 0, 10L),
            point(LocalDate.of(2026, 9, 8), 7, 3L),
            point(LocalDate.of(2026, 9, 9), 0, 999L),
            point(LocalDate.of(2026, 9, 9), 7, 999L));

        TrackRetentionResp resp = TrackRetentionMatrixBuilder.build(7, TODAY, boundary);

        TrackRetentionSummaryResp d7 = resp.getSummary().get(1);
        assertThat(d7.getDayOffset()).isEqualTo(7);
        // 目标日=今天（09-09 + 7 = 09-16）的 cohort 不得计入：若被算进去，基线会变成 1009 而不是 10
        assertThat(d7.getCohortSize()).isEqualTo(10L);
        assertThat(d7.getUserCount()).isEqualTo(3L);
        assertThat(d7.getRetentionRate()).isEqualByComparingTo(new BigDecimal("0.3"));
    }

    @Test
    void shouldKeepMatrixAndSummaryWindowsConsistent() {
        // 自洽性：矩阵的最大目标日与摘要的最大目标日必须是同一天（今天 - 1），
        // 两侧都不得把「今天」当成已过完的目标日
        TrackRetentionResp resp = TrackRetentionMatrixBuilder.build(7, TODAY, List.of());

        LocalDate lastMatrixCohort = LocalDate.parse(resp.getCohortDates().get(resp.getMatrixDays() - 1));
        int maxMatrixOffset = resp.getDayOffsets().get(resp.getDayOffsets().size() - 1);

        assertThat(lastMatrixCohort.plusDays(maxMatrixOffset)).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void shouldExposeD30OnlyForThirtyDayWindow() {
        TrackRetentionResp shortWindow = TrackRetentionMatrixBuilder.build(7, TODAY, List.of());
        assertThat(shortWindow.getSummary()).extracting(TrackRetentionSummaryResp::getDayOffset)
            .containsExactly(1, 7);

        TrackRetentionResp longWindow = TrackRetentionMatrixBuilder.build(30, TODAY, List.of());
        assertThat(longWindow.getSummary()).extracting(TrackRetentionSummaryResp::getDayOffset)
            .containsExactly(1, 7, 30);
        // days=30 时网格依旧固定 7×7（网格边长上限），只有摘要列扩到 D30
        assertThat(longWindow.getMatrixDays()).isEqualTo(7);
        assertThat(longWindow.getCohortDates()).hasSize(7);
    }

    @Test
    void shouldReturnNullRateWhenCohortIsEmpty() {
        TrackRetentionResp resp = TrackRetentionMatrixBuilder.build(1, TODAY, List.of());

        TrackRetentionRowResp row = resp.getRows().get(0);
        assertThat(row.getCohortSize()).isZero();
        // 分母为 0 时返回 null 表示「不可计算」，不伪造成 0%
        assertThat(row.getCells().get(0).getRetentionRate()).isNull();
        assertThat(resp.getSummary()).allSatisfy(summary -> assertThat(summary.getRetentionRate()).isNull());
    }

    @Test
    void shouldComputeEarliestCohortDateCoveringBothWindows() {
        // days=7：摘要窗口 09-02..09-08（末端 today-8），矩阵窗口 09-03..09-09，取更早的 09-02
        assertThat(TrackRetentionMatrixBuilder.earliestCohortDate(7, TODAY)).isEqualTo(LocalDate.of(2026, 9, 2));
        // days=30：摘要窗口回溯 30 天到 07-18，再加 30 天偏移上限（07-18 + 30 = 08-17 为窗口末端）
        assertThat(TrackRetentionMatrixBuilder.earliestCohortDate(30, TODAY)).isEqualTo(LocalDate.of(2026, 7, 18));
    }

    @Test
    void shouldIgnorePointsWithoutDateOrOffset() {
        List<TrackRetentionPointResp> broken = new ArrayList<>(points());
        broken.add(point(null, 1, 999L));
        broken.add(point(TODAY, null, 999L));

        TrackRetentionResp resp = TrackRetentionMatrixBuilder.build(7, TODAY, broken);

        assertThat(resp.getRows().stream()
            .flatMap(row -> row.getCells().stream())
            .map(TrackRetentionCellResp::getUserCount)
            .mapToLong(Long::longValue)
            .sum()).isEqualTo(200L);
    }

    @Test
    void shouldRejectNonPositiveDays() {
        assertThatThrownBy(() -> TrackRetentionMatrixBuilder.build(0, TODAY, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("分析天数");
        assertThatThrownBy(() -> TrackRetentionMatrixBuilder.earliestCohortDate(-1, TODAY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("分析天数");
    }

    private static List<TrackRetentionPointResp> points() {
        return List.of(
            point(LocalDate.of(2026, 9, 3), 0, 100L),
            point(LocalDate.of(2026, 9, 3), 1, 40L),
            point(LocalDate.of(2026, 9, 3), 6, 10L),
            point(LocalDate.of(2026, 9, 9), 0, 50L));
    }

    private static TrackRetentionPointResp point(LocalDate cohortDate, Integer dayOffset, Long userCount) {
        TrackRetentionPointResp point = new TrackRetentionPointResp();
        point.setCohortDate(cohortDate);
        point.setDayOffset(dayOffset);
        point.setUserCount(userCount);
        return point;
    }
}
