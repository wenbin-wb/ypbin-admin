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
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl implements AiUsageService {

    private final AiUsageLogMapper usageLogMapper;

    @Override
    public List<Map<String, Object>> dailyUsage(LocalDate startDate, LocalDate endDate) {
        Long tenantId = currentTenantId();
        LocalDateTime from = (startDate != null ? startDate : LocalDate.now().minusDays(29)).atStartOfDay();
        LocalDateTime to = (endDate != null ? endDate.plusDays(1) : LocalDate.now().plusDays(1)).atStartOfDay();
        // SQL 层按天聚合 Token 总量，避免窗口内全量行拉取后在 JVM 汇总
        return usageLogMapper.selectDailyTokensByTenant(tenantId, from, to).stream()
            .map(row -> Map.<String, Object>of(
                "date", row.get("statDate"),
                "tokens", ((Number) row.get("tokenTotal")).longValue()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> byModel() {
        Long tenantId = currentTenantId();
        // SQL 层按模型聚合 Token 总量，避免整租户数据全量拉取后在 JVM 汇总
        Map<String, Long> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : usageLogMapper.selectTokensGroupByModel(tenantId)) {
            String model = row.get("modelName") == null ? "未知" : row.get("modelName").toString();
            grouped.merge(model, ((Number) row.get("tokenTotal")).longValue(), Long::sum);
        }
        return grouped.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> Map.<String, Object>of("model", e.getKey(), "tokens", e.getValue()))
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> summary() {
        Long tenantId = currentTenantId();
        // SQL 层一次聚合调用数、Token 总量与正耗时均值，避免整租户数据全量拉取后在 JVM 汇总
        Map<String, Object> stats = usageLogMapper.selectSummaryStatsByTenant(tenantId);
        long totalCalls = ((Number) stats.get("totalCalls")).longValue();
        long totalTokens = ((Number) stats.get("tokenTotal")).longValue();
        double avgLatencyMs = ((Number) stats.get("avgLatencyMs")).doubleValue();
        return Map.of(
            "totalCalls", totalCalls,
            "totalTokens", totalTokens,
            "avgLatencyMs", Math.round(avgLatencyMs));
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}
