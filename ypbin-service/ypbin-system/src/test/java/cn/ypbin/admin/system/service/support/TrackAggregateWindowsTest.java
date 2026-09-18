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

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 聚合窗口测试（纯逻辑，不依赖数据库与系统时钟）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackAggregateWindowsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    @Test
    void shouldCoverRecentDaysInAscendingOrder() {
        List<LocalDate> dates = TrackAggregateWindows.recentDates(TODAY, 2);

        // 窗口必须覆盖「昨天」——延迟到达的事件会落在昨天，只算今天会永久漏算
        assertThat(dates).containsExactly(LocalDate.of(2026, 9, 15), TODAY);
    }

    @Test
    void shouldReturnSingleDayWhenWindowIsOne() {
        assertThat(TrackAggregateWindows.recentDates(TODAY, 1)).containsExactly(TODAY);
    }

    @Test
    void shouldRejectNonPositiveWindow() {
        assertThatThrownBy(() -> TrackAggregateWindows.recentDates(TODAY, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("窗口天数");
    }

    @Test
    void shouldKeepDefaultWindowWhenParamAbsent() {
        // 无参数时行为必须与改动前完全一致（最近 2 天），不能因为支持回填而改变默认窗口
        assertThat(TrackAggregateWindows.resolveDates(null, TODAY))
            .containsExactly(LocalDate.of(2026, 9, 15), TODAY);
        assertThat(TrackAggregateWindows.resolveDates("", TODAY))
            .containsExactly(LocalDate.of(2026, 9, 15), TODAY);
        assertThat(TrackAggregateWindows.resolveDates("   ", TODAY))
            .containsExactly(LocalDate.of(2026, 9, 15), TODAY);
    }

    @Test
    void shouldResolveSingleStatDate() {
        assertThat(TrackAggregateWindows.resolveDates("statDate=2026-09-01", TODAY))
            .containsExactly(LocalDate.of(2026, 9, 1));
        // 前后空白容忍
        assertThat(TrackAggregateWindows.resolveDates(" statDate = 2026-09-01 ", TODAY))
            .containsExactly(LocalDate.of(2026, 9, 1));
        // 今天允许（当天也要重算）
        assertThat(TrackAggregateWindows.resolveDates("statDate=2026-09-16", TODAY)).containsExactly(TODAY);
    }

    @Test
    void shouldResolveRecentDaysBackfill() {
        assertThat(TrackAggregateWindows.resolveDates("days=1", TODAY)).containsExactly(TODAY);

        List<LocalDate> thirty = TrackAggregateWindows.resolveDates("days=30", TODAY);
        assertThat(thirty).hasSize(30);
        assertThat(thirty.get(0)).isEqualTo(TODAY.minusDays(29));
        assertThat(thirty.get(29)).isEqualTo(TODAY);
    }

    @Test
    void shouldRejectInvalidBackfillParam() {
        // 禁静默兜底：以下每一种都必须抛错，而不是回落到默认窗口
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates("2026-09-01", TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("参数非法");
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates("statDate", TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("参数非法");
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates("statDate=", TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("参数非法");
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates("statDate=2026/09/01", TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("yyyy-MM-dd");
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates("statDate=2026-09-17", TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("未来日期");
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates("days=abc", TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("必须是整数");
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates("days=0", TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("days 必须在");
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates(
            "days=" + (TrackAggregateWindows.MAX_BACKFILL_DAYS + 1), TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("days 必须在");
        assertThatThrownBy(() -> TrackAggregateWindows.resolveDates("window=30", TODAY))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("参数非法");
    }
}
