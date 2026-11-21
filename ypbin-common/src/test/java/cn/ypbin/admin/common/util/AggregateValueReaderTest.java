/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AggregateValueReader} 的取值口径测试。
 *
 * <p>重点锁定两件事：① 驱动差异（Long/Integer/BigDecimal/BigInteger/字符串）都能读；
 * ② **无法解析时归零而不是抛异常** —— 这正是收敛前两处 {@code Long.parseLong} 缺 try 的真实风险
 * （聚合列出现 {@code "12.5"} / {@code "N/A"} 会打断整个看板渲染，CodeQL
 * {@code java/uncaught-number-format-exception} 报的就是它）。</p>
 *
 * @author wenbin
 * @since 2026-11-22
 */
class AggregateValueReaderTest {

    @Test
    @DisplayName("asLong：常见数值类型按 long 取值（含驱动返回的 BigDecimal/BigInteger）")
    void asLongHandlesNumericTypes() {
        assertThat(AggregateValueReader.asLong(null)).isZero();
        assertThat(AggregateValueReader.asLong(12L)).isEqualTo(12L);
        assertThat(AggregateValueReader.asLong(12)).isEqualTo(12L);
        assertThat(AggregateValueReader.asLong((short) 12)).isEqualTo(12L);
        assertThat(AggregateValueReader.asLong(new BigDecimal("12.7"))).isEqualTo(12L);
        assertThat(AggregateValueReader.asLong(new BigInteger("9007199254740993"))).isEqualTo(9007199254740993L);
        assertThat(AggregateValueReader.asLong(" 12 ")).isEqualTo(12L);
    }

    @Test
    @DisplayName("asLong：无法解析的脏数据归零，不抛异常（原实现会 NumberFormatException 打断渲染）")
    void asLongReturnsZeroOnUnparsableValues() {
        assertThat(AggregateValueReader.asLong("12.5")).isZero();
        assertThat(AggregateValueReader.asLong("N/A")).isZero();
        assertThat(AggregateValueReader.asLong("")).isZero();
        assertThat(AggregateValueReader.asLong("   ")).isZero();
        assertThat(AggregateValueReader.asLong("9999999999999999999999")).isZero();
    }

    @Test
    @DisplayName("asDouble：数值与字符串都可读；脏数据归零")
    void asDoubleHandlesNumericAndUnparsable() {
        assertThat(AggregateValueReader.asDouble(null)).isZero();
        assertThat(AggregateValueReader.asDouble(1.5D)).isEqualTo(1.5D);
        assertThat(AggregateValueReader.asDouble(new BigDecimal("2.25"))).isEqualTo(2.25D);
        assertThat(AggregateValueReader.asDouble(" 3.5 ")).isEqualTo(3.5D);
        assertThat(AggregateValueReader.asDouble("N/A")).isZero();
        assertThat(AggregateValueReader.asDouble("")).isZero();
    }

    @Test
    @DisplayName("asLong 与旧实现等价：合法数值的结果不变（防「收敛改坏口径」）")
    void asLongMatchesPreviousSemanticsForValidValues() {
        // 旧实现四档：BigDecimal.longValueExact / BigInteger.longValueExact / Number.longValue / parseLong
        // 新实现对前三档用 longValue()（不精确溢出即截断），此处锁定「无溢出时不改变结果」。
        assertThat(AggregateValueReader.asLong(new BigDecimal("100"))).isEqualTo(100L);
        assertThat(AggregateValueReader.asLong(new BigInteger("100"))).isEqualTo(100L);
        assertThat(AggregateValueReader.asLong(100)).isEqualTo(100L);
        assertThat(AggregateValueReader.asLong("100")).isEqualTo(100L);
    }
}
