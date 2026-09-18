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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 聚合任务的重算窗口与任务参数解析。
 *
 * <p><strong>为什么按天整段重算</strong>：事件可能延迟到达（消费者批量落库、客户端补发、
 * 离页兜底），只做增量会永久漏算。窗口取「最近 N 天」即可覆盖延迟，无需额外水位表，且天然自愈
 * ——任务失败后下次跑同一窗口就会补上。</p>
 *
 * <p><strong>为什么必须有手工回填参数</strong>：默认窗口只有 2 天，只能覆盖「按小时触发」的延迟。
 * 若任务中断超过窗口长度（停机、调度中心故障、参数写错），那些天永远不会再被重算，聚合表留下
 * <strong>永久空洞</strong>；阶段 1/2 已落库的历史明细也需要一次性补进聚合表。故任务支持
 * {@code statDate=yyyy-MM-dd}（补某一天）与 {@code days=N}（重算最近 N 天）两种参数。</p>
 *
 * <p>抽成独立类是为了能在<strong>不依赖数据库、调度框架与时钟</strong>的前提下测试窗口边界与参数解析：
 * 调用方把 {@code today} 与参数串传进来，本类不自己取 {@code LocalDate.now()}、不读 {@code XxlJobHelper}。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackAggregateWindows {

    /** 窗口天数下限 */
    private static final int MIN_WINDOW_DAYS = 1;

    /** 无参数时的默认重算窗口：覆盖「按小时触发」下延迟到达的事件，2 天足够 */
    public static final int DEFAULT_WINDOW_DAYS = 2;

    /**
     * 手工回填窗口天数上限。
     *
     * <p>与查询侧统计天数上限（{@code TrackQueryParams.MAX_DAYS} = 90）对齐：一次回填超过 90 天
     * 意味着连续跑 90 个以上独立事务，失败面与耗时都不可控。需要更长历史时用 {@code statDate}
     * 分次指定（每次一天，失败只影响那一天）。</p>
     */
    public static final int MAX_BACKFILL_DAYS = 90;

    /** 任务参数：指定单个统计日期 */
    private static final String PARAM_STAT_DATE = "statDate";

    /** 任务参数：指定最近 N 天 */
    private static final String PARAM_DAYS = "days";

    /** 参数键值分隔符 */
    private static final char PARAM_SEPARATOR = '=';

    private TrackAggregateWindows() {
    }

    /**
     * 计算需要重算的日期（升序，含结束日）。
     *
     * @param today 结束日期（通常是今天）
     * @param days  窗口天数，必须为正
     * @return 从 {@code today - days + 1} 到 {@code today} 的连续日期
     */
    public static List<LocalDate> recentDates(LocalDate today, int days) {
        if (days < MIN_WINDOW_DAYS) {
            throw new IllegalArgumentException("聚合窗口天数必须为正数，当前为 " + days);
        }
        List<LocalDate> dates = new ArrayList<>(days);
        LocalDate startDate = today.minusDays(days - 1L);
        for (int index = 0; index < days; index++) {
            dates.add(startDate.plusDays(index));
        }
        return dates;
    }

    /**
     * 由 XXL-JOB 任务参数推导需要重算的日期。
     *
     * <p>支持的参数（先 {@code trim}，键名大小写敏感）：</p>
     * <ul>
     *   <li>空 / {@code null}：默认窗口，即最近 {@link #DEFAULT_WINDOW_DAYS} 天——<strong>既有行为不变</strong>；</li>
     *   <li>{@code statDate=2026-09-01}：只重算这一天（手工补历史空洞）；</li>
     *   <li>{@code days=30}：重算最近 30 天（含今天），1 ≤ N ≤ {@link #MAX_BACKFILL_DAYS}。</li>
     * </ul>
     *
     * <p>参数非法时<strong>显式抛错</strong>（不静默回落到默认窗口）：静默兜底会让「以为补了 30 天、
     * 实际只补了 2 天」这种事发生在无人察觉的地方，而聚合表一旦少算就是错数字。</p>
     *
     * @param jobParam 任务参数原始串（可为 null / 空）
     * @param today    今天
     * @return 需要重算的日期（升序）
     * @throws IllegalArgumentException 参数格式非法、键名未知、日期不可解析、日期在未来或天数越界
     */
    public static List<LocalDate> resolveDates(String jobParam, LocalDate today) {
        if (jobParam == null || jobParam.isBlank()) {
            return recentDates(today, DEFAULT_WINDOW_DAYS);
        }
        String param = jobParam.trim();
        int separator = param.indexOf(PARAM_SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException(invalidParamMessage(param));
        }
        String key = param.substring(0, separator).trim();
        String value = param.substring(separator + 1).trim();
        if (PARAM_STAT_DATE.equals(key)) {
            return List.of(parseStatDate(value, today, param));
        }
        if (PARAM_DAYS.equals(key)) {
            return recentDates(today, parseDays(value, param));
        }
        throw new IllegalArgumentException(invalidParamMessage(param));
    }

    /**
     * 解析单个统计日期。
     *
     * @param value 日期文本
     * @param today 今天
     * @param param 原始参数（用于报错信息）
     * @return 统计日期
     */
    private static LocalDate parseStatDate(String value, LocalDate today, String param) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(invalidParamMessage(param));
        }
        LocalDate statDate;
        try {
            statDate = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                invalidParamMessage(param) + "；statDate 必须是 yyyy-MM-dd 格式，当前为 " + value, e);
        }
        if (statDate.isAfter(today)) {
            throw new IllegalArgumentException(
                invalidParamMessage(param) + "；statDate 不能是未来日期（今天为 " + today + "），当前为 " + statDate);
        }
        return statDate;
    }

    /**
     * 解析回填天数。
     *
     * @param value 天数文本
     * @param param 原始参数（用于报错信息）
     * @return 天数
     */
    private static int parseDays(String value, String param) {
        int days;
        try {
            days = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                invalidParamMessage(param) + "；days 必须是整数，当前为 " + value, e);
        }
        if (days < MIN_WINDOW_DAYS || days > MAX_BACKFILL_DAYS) {
            throw new IllegalArgumentException(invalidParamMessage(param) + "；days 必须在 "
                + MIN_WINDOW_DAYS + " 到 " + MAX_BACKFILL_DAYS + " 之间，当前为 " + days);
        }
        return days;
    }

    private static String invalidParamMessage(String param) {
        return "埋点聚合任务参数非法：" + param + "；允许的形式为 '" + PARAM_STAT_DATE
            + "=yyyy-MM-dd'（单天）或 '" + PARAM_DAYS + "=N'（最近 N 天，1~" + MAX_BACKFILL_DAYS
            + "），留空则使用默认最近 " + DEFAULT_WINDOW_DAYS + " 天";
    }
}
