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

import cn.ypbin.admin.system.model.resp.TrackTrendResp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 趋势补零测试（纯逻辑，不依赖数据库）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackTrendFillerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    @Test
    void shouldFillMissingDatesWithZero() {
        Map<String, Long> counts = Map.of("2026-09-14", 5L, "2026-09-16", 9L);

        List<TrackTrendResp> rows = TrackTrendFiller.fill(3, TODAY, counts);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getDate()).isEqualTo("2026-09-14");
        assertThat(rows.get(0).getCount()).isEqualTo(5L);
        // 中间这天没有数据，必须补 0 而不是跳过——跳过会让前端把两点直接连线
        assertThat(rows.get(1).getDate()).isEqualTo("2026-09-15");
        assertThat(rows.get(1).getCount()).isZero();
        assertThat(rows.get(2).getDate()).isEqualTo("2026-09-16");
        assertThat(rows.get(2).getCount()).isEqualTo(9L);
    }

    @Test
    void shouldStartFromRequestedWindow() {
        List<TrackTrendResp> rows = TrackTrendFiller.fill(1, TODAY, Map.of());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDate()).isEqualTo("2026-09-16");
        assertThat(rows.get(0).getCount()).isZero();
    }

    @Test
    void shouldRejectNonPositiveDays() {
        assertThatThrownBy(() -> TrackTrendFiller.fill(0, TODAY, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("统计天数");
    }

    @Test
    void shouldIndexRowsByDateAndSkipIncompleteRows() {
        TrackTrendResp complete = new TrackTrendResp();
        complete.setDate("2026-09-15");
        complete.setCount(3L);
        TrackTrendResp missingCount = new TrackTrendResp();
        missingCount.setDate("2026-09-16");

        Map<String, Long> indexed = TrackTrendFiller.indexByDate(List.of(complete, missingCount));

        assertThat(indexed).containsOnlyKeys("2026-09-15").containsEntry("2026-09-15", 3L);
    }
}
