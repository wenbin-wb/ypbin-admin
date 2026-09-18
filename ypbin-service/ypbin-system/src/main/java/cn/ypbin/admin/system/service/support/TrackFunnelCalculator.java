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

import cn.ypbin.admin.system.model.resp.TrackFunnelStepResp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话级漏斗计算（单调推进）。
 *
 * <p><strong>口径</strong>：同一会话内按时间顺序出现全部步骤，允许中间夹其它事件；
 * 命中当前步骤即指针前移，<strong>不回退</strong>（先出现的 B 不能顶替尚未命中的 A）。
 * 不做跨会话的用户级漏斗。</p>
 *
 * <p><strong>重复步骤码</strong>：{@code steps=a,a} 需要会话内出现两次 {@code a} 才算完成两步——
 * 一次出现只能满足一步，这是「按序推进」的必然推论。</p>
 *
 * <p>抽成独立类是为了能在<strong>不依赖数据库</strong>的前提下覆盖推进/回退/重复步骤等边界。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackFunnelCalculator {

    /** 转化率保留小数位 */
    private static final int RATE_SCALE = 6;

    /** 转化率舍入方式 */
    private static final RoundingMode RATE_ROUNDING = RoundingMode.HALF_UP;

    /** 首步转化率的固定值 */
    private static final BigDecimal FULL_RATE = BigDecimal.ONE;

    private TrackFunnelCalculator() {
    }

    /**
     * 计算单个会话走到的步骤数。
     *
     * @param steps    漏斗步骤事件码（有序）
     * @param sequence 会话内按时间升序的事件码
     * @return 已完成的步骤数（0 ~ {@code steps.size()}）
     */
    public static int reachedSteps(List<String> steps, List<String> sequence) {
        int reached = 0;
        for (String eventCode : sequence) {
            if (reached >= steps.size()) {
                break;
            }
            if (steps.get(reached).equals(eventCode)) {
                reached++;
            }
        }
        return reached;
    }

    /**
     * 汇总各步骤的会话数与转化率。
     *
     * @param steps     漏斗步骤事件码（有序，调用方已校验 2..8 个）
     * @param sequences 各会话的有序事件码
     * @return 每步一行；首步会话数为 0 时各步转化率为 {@code null}（分母缺失，不伪造成 0）
     */
    public static List<TrackFunnelStepResp> calculate(List<String> steps, List<List<String>> sequences) {
        int stepCount = steps.size();
        long[] counts = new long[stepCount];
        for (List<String> sequence : sequences) {
            int reached = reachedSteps(steps, sequence);
            for (int index = 0; index < reached; index++) {
                counts[index]++;
            }
        }
        long firstStepCount = counts[0];
        List<TrackFunnelStepResp> result = new ArrayList<>(stepCount);
        for (int index = 0; index < stepCount; index++) {
            TrackFunnelStepResp step = new TrackFunnelStepResp();
            step.setStepIndex(index + 1);
            step.setEventCode(steps.get(index));
            step.setSessionCount(counts[index]);
            step.setConversionRate(toRate(counts[index], firstStepCount));
            result.add(step);
        }
        return result;
    }

    /**
     * 统计被截断的会话数。
     *
     * <p>截断的会话可能丢失了后续步骤，会被当成「没走到该步」——所以各步会话数只是下限。
     * 把数量暴露给调用方，客户端才能显示「至少」提示（口径见方案第五节第 5 条）。</p>
     *
     * @param truncatedFlags 各会话的截断标记（{@code 1} 表示已截断；{@code null} 视为未截断）
     * @return 被截断的会话数
     */
    public static long countTruncatedSessions(List<Integer> truncatedFlags) {
        if (truncatedFlags == null) {
            return 0L;
        }
        return truncatedFlags.stream()
            .filter(flag -> flag != null && flag == TrackSessionAssembler.TRUNCATED_FLAG)
            .count();
    }

    private static BigDecimal toRate(long count, long total) {
        if (total <= 0) {
            return null;
        }
        if (count == total) {
            return FULL_RATE;
        }
        return BigDecimal.valueOf(count).divide(BigDecimal.valueOf(total), RATE_SCALE, RATE_ROUNDING);
    }
}
