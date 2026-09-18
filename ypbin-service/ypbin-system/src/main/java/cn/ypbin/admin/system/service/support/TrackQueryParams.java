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

import cn.ypbin.starter.core.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;

/**
 * 埋点分析查询参数校验。
 *
 * <p>抽成独立工具类是为了能在<strong>不依赖数据库</strong>的前提下测试这些边界：
 * 天数上限既防误传（前端写错）也防慢查询（明细表按天聚合扫描），条数上限防一次拉回过多数据，
 * 漏斗步骤数上限既防慢查询也防空结果误导。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackQueryParams {

    /** 统计天数下限 */
    private static final int MIN_DAYS = 1;

    /** 统计天数上限 */
    private static final int MAX_DAYS = 90;

    /** 排行条数下限 */
    private static final int MIN_LIMIT = 1;

    /** 排行条数上限 */
    private static final int MAX_LIMIT = 50;

    /** 漏斗步骤数下限：单步不成漏斗 */
    private static final int MIN_FUNNEL_STEPS = 2;

    /** 漏斗步骤数上限：步骤越多命中率越低，超过这个数量结果基本都是 0，没必要扫 */
    private static final int MAX_FUNNEL_STEPS = 8;

    private TrackQueryParams() {
    }

    /**
     * 校验统计天数。
     *
     * @param days 天数
     */
    public static void requireDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BusinessException("统计天数必须在 " + MIN_DAYS + " 到 " + MAX_DAYS + " 之间");
        }
    }

    /**
     * 校验排行条数。
     *
     * @param limit 条数
     */
    public static void requireLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new BusinessException("排行条数必须在 " + MIN_LIMIT + " 到 " + MAX_LIMIT + " 之间");
        }
    }

    /**
     * 解析并校验漏斗步骤。
     *
     * <p>分隔符与序列存储用的是同一个常量（{@link TrackEventSequenceBuilder#SEQUENCE_SEPARATOR}）：
     * 生成侧禁止事件码含该字符、查询侧按该字符切分、SQL 侧用 {@code FIND_IN_SET} 匹配，
     * 三处必须同一口径，否则会出现「存得下但查不出」。</p>
     *
     * @param steps 逗号分隔的事件码
     * @return 步骤事件码（保留请求顺序）
     * @throws BusinessException 为空、含空事件码、或步骤数不在 2..8
     */
    public static List<String> parseSteps(String steps) {
        if (steps == null || steps.isBlank()) {
            throw new BusinessException("漏斗步骤不能为空，请传逗号分隔的事件码");
        }
        String[] parts = steps.split(TrackEventSequenceBuilder.SEQUENCE_SEPARATOR, -1);
        List<String> stepCodes = new ArrayList<>(parts.length);
        for (String part : parts) {
            String eventCode = part.trim();
            if (eventCode.isEmpty()) {
                throw new BusinessException("漏斗步骤存在空事件码：" + steps);
            }
            stepCodes.add(eventCode);
        }
        if (stepCodes.size() < MIN_FUNNEL_STEPS || stepCodes.size() > MAX_FUNNEL_STEPS) {
            throw new BusinessException("漏斗步骤数必须在 " + MIN_FUNNEL_STEPS + " 到 " + MAX_FUNNEL_STEPS
                + " 之间，当前为 " + stepCodes.size());
        }
        return stepCodes;
    }
}
