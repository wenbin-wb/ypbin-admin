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

import cn.ypbin.admin.system.model.resp.TrackFunnelStepResp;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 漏斗单调推进测试（纯逻辑，不依赖数据库）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackFunnelCalculatorTest {

    private static final List<String> STEPS = List.of("a", "b", "c");

    @Test
    void shouldAdvanceThroughStepsInOrderAllowingNoise() {
        assertThat(TrackFunnelCalculator.reachedSteps(STEPS, List.of("a", "x", "b", "y", "c"))).isEqualTo(3);
    }

    @Test
    void shouldNotGoBackwards() {
        // 先出现的 b 不能顶替尚未命中的 a；命中 a 之后指针在 b，后面的 c 也无法跳过 b
        assertThat(TrackFunnelCalculator.reachedSteps(STEPS, List.of("b", "a", "c"))).isEqualTo(1);
    }

    @Test
    void shouldStopAtMissingStep() {
        assertThat(TrackFunnelCalculator.reachedSteps(STEPS, List.of("a", "b"))).isEqualTo(2);
        assertThat(TrackFunnelCalculator.reachedSteps(STEPS, List.of("a"))).isEqualTo(1);
        assertThat(TrackFunnelCalculator.reachedSteps(STEPS, List.of("z"))).isZero();
        assertThat(TrackFunnelCalculator.reachedSteps(STEPS, List.of())).isZero();
    }

    @Test
    void shouldRequireSeparateOccurrencesForRepeatedStep() {
        // 步骤重复时，一次出现只能满足一步
        assertThat(TrackFunnelCalculator.reachedSteps(List.of("a", "a"), List.of("a"))).isEqualTo(1);
        assertThat(TrackFunnelCalculator.reachedSteps(List.of("a", "a"), List.of("a", "a", "a"))).isEqualTo(2);
    }

    @Test
    void shouldAggregateCountsAndConversionRates() {
        List<List<String>> sequences = List.of(
            List.of("a", "b", "c"),
            List.of("a", "b"),
            List.of("a"),
            List.of("z"));

        List<TrackFunnelStepResp> steps = TrackFunnelCalculator.calculate(STEPS, sequences);

        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).getStepIndex()).isEqualTo(1);
        assertThat(steps.get(0).getEventCode()).isEqualTo("a");
        assertThat(steps.get(0).getSessionCount()).isEqualTo(3L);
        assertThat(steps.get(0).getConversionRate()).isEqualByComparingTo(BigDecimal.ONE);

        assertThat(steps.get(1).getSessionCount()).isEqualTo(2L);
        assertThat(steps.get(1).getConversionRate())
            .isEqualByComparingTo(new BigDecimal("0.666667"));

        assertThat(steps.get(2).getSessionCount()).isEqualTo(1L);
        assertThat(steps.get(2).getConversionRate())
            .isEqualByComparingTo(new BigDecimal("0.333333"));
    }

    @Test
    void shouldReturnNullRateWhenFirstStepMissing() {
        List<TrackFunnelStepResp> steps = TrackFunnelCalculator.calculate(STEPS, List.of(List.of("z")));

        assertThat(steps).allSatisfy(step -> assertThat(step.getSessionCount()).isZero());
        // 分母不存在时返回 null 表示「不可计算」，不伪造成 0%
        assertThat(steps).allSatisfy(step -> assertThat(step.getConversionRate()).isNull());
    }

    @Test
    void shouldCountTruncatedSessions() {
        // 漏斗数字只是下限：被截断的会话可能丢了后续步骤，必须把数量暴露出去
        assertThat(TrackFunnelCalculator.countTruncatedSessions(List.of(1, 0, 1))).isEqualTo(2L);
        assertThat(TrackFunnelCalculator.countTruncatedSessions(List.of(0, 0))).isZero();
        assertThat(TrackFunnelCalculator.countTruncatedSessions(List.of())).isZero();
        // 标记列可空时不得抛 NPE，且不把 null 当成已截断
        assertThat(TrackFunnelCalculator.countTruncatedSessions(Arrays.asList(1, null))).isEqualTo(1L);
        assertThat(TrackFunnelCalculator.countTruncatedSessions(null)).isZero();
    }
}
