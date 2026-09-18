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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 会话事件序列拼接与截断测试（纯逻辑，不依赖数据库）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackEventSequenceBuilderTest {

    @Test
    void shouldJoinEventCodesInOrder() {
        TrackEventSequenceBuilder.Sequence sequence =
            TrackEventSequenceBuilder.build(List.of("ui.page.view", "ui.click", "api.call"));

        assertThat(sequence.value()).isEqualTo("ui.page.view,ui.click,api.call");
        assertThat(sequence.truncated()).isFalse();
    }

    @Test
    void shouldNotTruncateAtExactLimit() {
        List<String> codes = codes(TrackEventSequenceBuilder.MAX_SEQUENCE_EVENTS);

        TrackEventSequenceBuilder.Sequence sequence = TrackEventSequenceBuilder.build(codes);

        assertThat(TrackEventSequenceBuilder.parse(sequence.value()))
            .hasSize(TrackEventSequenceBuilder.MAX_SEQUENCE_EVENTS);
        assertThat(sequence.truncated()).isFalse();
    }

    @Test
    void shouldTruncateAndFlagWhenOverLimit() {
        List<String> codes = codes(TrackEventSequenceBuilder.MAX_SEQUENCE_EVENTS + 1);
        codes.set(0, "first");
        codes.set(TrackEventSequenceBuilder.MAX_SEQUENCE_EVENTS, "overflow");

        TrackEventSequenceBuilder.Sequence sequence = TrackEventSequenceBuilder.build(codes);

        List<String> parsed = TrackEventSequenceBuilder.parse(sequence.value());
        assertThat(parsed).hasSize(TrackEventSequenceBuilder.MAX_SEQUENCE_EVENTS);
        assertThat(parsed.get(0)).isEqualTo("first");
        // 第 51 个事件码不得出现：否则行会被撑大，且截断标记会名不副实
        assertThat(parsed).doesNotContain("overflow");
        assertThat(sequence.truncated()).isTrue();
    }

    @Test
    void shouldRejectEventCodeContainingSeparator() {
        // 含分隔符的事件码会让序列无法还原、也让 SQL 的 FIND_IN_SET 匹配错位，必须显式报错而非静默替换
        assertThatThrownBy(() -> TrackEventSequenceBuilder.build(List.of("a,b")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("分隔符");
    }

    @Test
    void shouldRejectBlankEventCode() {
        assertThatThrownBy(() -> TrackEventSequenceBuilder.build(Arrays.asList("a", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("事件码为空");
        assertThatThrownBy(() -> TrackEventSequenceBuilder.build(List.of("a", "")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("事件码为空");
    }

    @Test
    void shouldRejectNullList() {
        assertThatThrownBy(() -> TrackEventSequenceBuilder.build(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能为 null");
    }

    @Test
    void shouldParseEmptySequenceToEmptyList() {
        assertThat(TrackEventSequenceBuilder.parse(null)).isEmpty();
        assertThat(TrackEventSequenceBuilder.parse("")).isEmpty();
    }

    @Test
    void shouldParseSequenceBackToCodes() {
        assertThat(TrackEventSequenceBuilder.parse("a,b,c")).containsExactly("a", "b", "c");
    }

    private static List<String> codes(int size) {
        List<String> codes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            codes.add("code-" + index);
        }
        return codes;
    }
}
