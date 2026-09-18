/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service.impl;

import cn.ypbin.admin.ai.mapper.AiUsageLogMapper;
import cn.ypbin.admin.ai.service.AiUsageService;
import cn.ypbin.starter.ai.chat.usage.AiUsageOutcome;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI Token 用量统计服务实现。
 *
 * <p><b>未知用量（token 为 NULL）的处理口径</b>：{@code ai_usage_log} 的 token 列为 {@code NULL}
 * 表示「上游未回报用量」，与真实 0 不可区分。聚合 SQL 一律直接 {@code SUM(total_tokens)}，
 * 绝不在聚合参数上套 {@code COALESCE}（那会把未知行当 0 参与求和与均值）；本层为图表连续性把
 * 「该分组没有任何已回报用量」显示为 0，同时<b>每个响应都带未知条数</b>
 * （{@code unknownCalls} / {@code unknownTokenCalls}），使「无已回报用量」与「真实 0 用量」可区分。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl implements AiUsageService {

    /** 上游未回报模型名时的展示标签（与真实模型名区分开，不伪造模型） */
    private static final String UNKNOWN_MODEL_LABEL = "未知";

    private final AiUsageLogMapper usageLogMapper;

    @Override
    public List<Map<String, Object>> dailyUsage(LocalDate startDate, LocalDate endDate) {
        Long tenantId = currentTenantId();
        LocalDateTime from = (startDate != null ? startDate : LocalDate.now().minusDays(29)).atStartOfDay();
        LocalDateTime to = (endDate != null ? endDate.plusDays(1) : LocalDate.now().plusDays(1)).atStartOfDay();
        // SQL 层按天聚合 Token 总量与未知条数，避免窗口内全量行拉取后在 JVM 汇总
        return usageLogMapper.selectDailyTokensByTenant(tenantId, from, to).stream()
            .map(row -> Map.<String, Object>of(
                "date", row.get("statDate"),
                "tokens", toLong(row.get("tokenTotal")),
                "unknownCalls", toLong(row.get("unknownCalls"))))
            .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> byModel() {
        Long tenantId = currentTenantId();
        // SQL 层按模型聚合 Token 总量与未知条数，避免整租户数据全量拉取后在 JVM 汇总
        Map<String, Long> grouped = new LinkedHashMap<>();
        Map<String, Long> unknownByModel = new LinkedHashMap<>();
        for (Map<String, Object> row : usageLogMapper.selectTokensGroupByModel(tenantId)) {
            String model = row.get("modelName") == null ? UNKNOWN_MODEL_LABEL : row.get("modelName").toString();
            grouped.merge(model, toLong(row.get("tokenTotal")), Long::sum);
            unknownByModel.merge(model, toLong(row.get("unknownCalls")), Long::sum);
        }
        return grouped.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> Map.<String, Object>of(
                "model", e.getKey(),
                "tokens", e.getValue(),
                "unknownCalls", unknownByModel.getOrDefault(e.getKey(), 0L)))
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> summary() {
        Long tenantId = currentTenantId();
        // SQL 层一次聚合调用数、已回报 Token 总量、未知条数、三类终局结果与正耗时均值，避免整租户全量拉取后在 JVM 汇总
        Map<String, Object> stats = usageLogMapper.selectSummaryStatsByTenant(tenantId,
            AiUsageOutcome.SUCCESS.code(), AiUsageOutcome.FAILURE.code(), AiUsageOutcome.CANCELLED.code());
        return Map.of(
            "totalCalls", toLong(stats.get("totalCalls")),
            // 0 = 没有任何已回报用量（不等于「真实 0 用量」）；未知条数见 unknownTokenCalls
            "totalTokens", toLong(stats.get("tokenTotal")),
            "unknownTokenCalls", toLong(stats.get("unknownTokenCalls")),
            "successCalls", toLong(stats.get("successCalls")),
            "failureCalls", toLong(stats.get("failureCalls")),
            "cancelledCalls", toLong(stats.get("cancelledCalls")),
            "avgLatencyMs", Math.round(toDouble(stats.get("avgLatencyMs"))));
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }

    /**
     * SQL 聚合列取值：{@code NULL}（无任何已回报用量）按 0 展示，未知条数由响应里的 unknown 计数承载。
     */
    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }
}
