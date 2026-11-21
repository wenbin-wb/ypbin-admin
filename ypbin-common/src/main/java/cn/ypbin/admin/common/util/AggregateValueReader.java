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

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 聚合值读取（SQL 聚合列 → long），全仓单一实现。
 *
 * <p><b>为什么必须只有一份</b>：同一段 {@code instanceof BigDecimal/BigInteger/Number → parse} 的逻辑
 * 此前在 4 个实现类里各写一份（{@code IotDashboardServiceImpl}、{@code IotDashboardSnapshotServiceImpl}、
 * {@code AvailabilityServiceImpl}、{@code AiStatsServiceImpl}），其中两处最后一档
 * {@code Long.parseLong(value.toString().trim())} **没有捕获异常** —— 聚合列若因脏数据/驱动差异
 * 返回 {@code "12.5"} 或 {@code "N/A"}，会抛 {@code NumberFormatException} 直接打断整个看板渲染
 * （CodeQL {@code java/uncaught-number-format-exception} 报的正是这两处）。收敛到一处后，
 * 兜底口径只有一个定义点，修一次即全仓生效。</p>
 *
 * <p><b>兜底口径</b>：无法解析时返回 0 并<b>不</b>抛异常 —— 与同包 {@link CachedCounterReader}
 * 「计数器损坏不该让整条链路不可用」的取舍一致；但本类不打日志（调用方在聚合循环里高频调用，
 * 逐条 WARN 会把日志淹掉），调用方可按需自行判断。</p>
 *
 * @author wenbin
 * @since 2026-11-22
 */
public final class AggregateValueReader {

    private AggregateValueReader() {
    }

    /**
     * 把聚合列取值转成 {@code long}。
     *
     * @param value 聚合值（可能为 {@code null}）
     * @return 转换结果；{@code null} 或无法解析时返回 {@code 0}
     */
    public static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.longValue();
        }
        if (value instanceof BigInteger integer) {
            return integer.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ignored) {
            // 已知的脏数据形态（"12.5"、"N/A"、空串）：按「无数据」口径归零，不让单个聚合值打断整页
            return 0L;
        }
    }

    /**
     * 把聚合列取值转成 {@code double}（AI 用量类图表用）。
     *
     * @param value 聚合值（可能为 {@code null}）
     * @return 转换结果；{@code null} 或无法解析时返回 {@code 0}
     */
    public static double asDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }
}
