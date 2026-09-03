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

import cn.ypbin.admin.ai.entity.AiUsageLog;
import cn.ypbin.admin.ai.mapper.AiUsageLogMapper;
import cn.ypbin.admin.ai.service.AiUsageService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        List<AiUsageLog> logs = usageLogMapper.selectList(
            new LambdaQueryWrapper<AiUsageLog>()
                .eq(AiUsageLog::getTenantId, tenantId)
                .between(AiUsageLog::getCreateTime, from, to)
                .orderByAsc(AiUsageLog::getCreateTime));
        Map<String, Long> dailyMap = new LinkedHashMap<>();
        logs.forEach(log -> {
            String day = log.getCreateTime().toLocalDate().toString();
            dailyMap.merge(day, log.getTotalTokens() == null ? 0L : log.getTotalTokens().longValue(), Long::sum);
        });
        return dailyMap.entrySet().stream()
            .map(e -> Map.<String, Object>of("date", e.getKey(), "tokens", e.getValue()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> byModel() {
        Long tenantId = currentTenantId();
        List<AiUsageLog> logs = usageLogMapper.selectList(
            new LambdaQueryWrapper<AiUsageLog>()
                .eq(AiUsageLog::getTenantId, tenantId));
        Map<String, Long> grouped = logs.stream()
            .collect(Collectors.groupingBy(
                l -> l.getModelName() != null ? l.getModelName() : "未知",
                Collectors.summingLong(l -> l.getTotalTokens() == null ? 0L : l.getTotalTokens().longValue())));
        return grouped.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> Map.<String, Object>of("model", e.getKey(), "tokens", e.getValue()))
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> summary() {
        Long tenantId = currentTenantId();
        List<AiUsageLog> logs = usageLogMapper.selectList(
            new LambdaQueryWrapper<AiUsageLog>().eq(AiUsageLog::getTenantId, tenantId));
        long totalTokens = logs.stream()
            .mapToLong(l -> l.getTotalTokens() == null ? 0L : l.getTotalTokens().longValue()).sum();
        double avgLatency = logs.stream()
            .filter(l -> l.getLatencyMs() != null && l.getLatencyMs() > 0)
            .mapToLong(AiUsageLog::getLatencyMs).average().orElse(0);
        return Map.of(
            "totalCalls", logs.size(),
            "totalTokens", totalTokens,
            "avgLatencyMs", Math.round(avgLatency));
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}
