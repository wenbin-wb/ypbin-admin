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

import cn.ypbin.admin.system.model.resp.TrackTrendResp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 趋势序列补零。
 *
 * <p>聚合 SQL 只返回「有数据的日期」，而趋势图需要连续的日期轴——缺的日期必须补 0，
 * 否则前端会把相邻两点直接连线，把「某天没有数据」误读成「数据平滑过渡」。</p>
 *
 * <p>抽成独立工具是为了能在<strong>不依赖数据库</strong>的前提下测试这段纯逻辑。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackTrendFiller {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TrackTrendFiller() {
    }

    /**
     * 按日期补零成连续序列。
     *
     * @param days        天数（含结束日），必须为正
     * @param today       结束日期（通常是今天）
     * @param countByDate 已有计数，键为 {@code yyyy-MM-dd}
     * @return 从 {@code today - days + 1} 到 {@code today} 的连续序列
     */
    public static List<TrackTrendResp> fill(int days, LocalDate today, Map<String, Long> countByDate) {
        if (days <= 0) {
            throw new IllegalArgumentException("统计天数必须为正数，当前为 " + days);
        }
        LocalDate startDate = today.minusDays(days - 1L);
        List<TrackTrendResp> result = new ArrayList<>(days);
        for (int index = 0; index < days; index++) {
            String date = startDate.plusDays(index).format(DATE_FORMATTER);
            TrackTrendResp item = new TrackTrendResp();
            item.setDate(date);
            item.setCount(countByDate.getOrDefault(date, 0L));
            result.add(item);
        }
        return result;
    }

    /**
     * 把聚合结果转成按日期索引的映射，便于补零时按日期取值。
     *
     * @param rows 聚合结果（日期或计数为 null 的行会被忽略）
     * @return 日期到计数的映射
     */
    public static Map<String, Long> indexByDate(List<TrackTrendResp> rows) {
        return rows.stream()
            .filter(row -> row.getDate() != null && row.getCount() != null)
            .collect(Collectors.toMap(TrackTrendResp::getDate, TrackTrendResp::getCount,
                (first, second) -> second, LinkedHashMap::new));
    }
}
