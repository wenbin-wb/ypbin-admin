/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service.impl;

import cn.ypbin.admin.system.ai.entity.AiDocument;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.entity.AiQueryLog;
import cn.ypbin.admin.system.ai.entity.AiUsageLog;
import cn.ypbin.admin.system.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.system.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.system.ai.mapper.AiQueryLogMapper;
import cn.ypbin.admin.system.ai.mapper.AiUsageLogMapper;
import cn.ypbin.admin.system.ai.service.AiStatsService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 统计看板服务实现。
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Service
@RequiredArgsConstructor
public class AiStatsServiceImpl implements AiStatsService {

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final AiUsageLogMapper usageLogMapper;
    private final AiQueryLogMapper queryLogMapper;

    @Override
    public Map<String, Object> summary() {
        Long tenantId = currentTenantId();
        long kbCount = kbMapper.selectCount(
            new LambdaQueryWrapper<AiKnowledgeBase>().eq(AiKnowledgeBase::getTenantId, tenantId));
        long docTotal = documentMapper.selectCount(
            new LambdaQueryWrapper<AiDocument>().eq(AiDocument::getTenantId, tenantId));
        // SQL 聚合对话数与 Token 总量，避免全表拉取
        Map<String, Object> usage = usageLogMapper.selectSummaryByTenant(tenantId);
        long chatCount = usage == null || usage.get("chatCount") == null
            ? 0L : ((Number) usage.get("chatCount")).longValue();
        long tokenTotal = usage == null || usage.get("tokenTotal") == null
            ? 0L : ((Number) usage.get("tokenTotal")).longValue();
        long queryCount = queryLogMapper.selectCount(
            new LambdaQueryWrapper<AiQueryLog>().eq(AiQueryLog::getTenantId, tenantId));
        return Map.of(
            "kbCount", kbCount,
            "docTotal", docTotal,
            "chatCount", chatCount,
            "queryCount", queryCount,
            "tokenTotal", tokenTotal);
    }

    @Override
    public List<Map<String, Object>> daily(int days) {
        Long tenantId = currentTenantId();
        int range = days > 0 && days <= 90 ? days : 30;
        LocalDateTime from = LocalDate.now().minusDays(range - 1L).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        List<AiUsageLog> usageLogs = usageLogMapper.selectList(
            new LambdaQueryWrapper<AiUsageLog>()
                .eq(AiUsageLog::getTenantId, tenantId)
                .between(AiUsageLog::getCreateTime, from, to));
        List<AiQueryLog> queryLogs = queryLogMapper.selectList(
            new LambdaQueryWrapper<AiQueryLog>()
                .eq(AiQueryLog::getTenantId, tenantId)
                .between(AiQueryLog::getCreateTime, from, to));

        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        // 预填充缺失日期，保证趋势图连续
        for (int i = 0; i < range; i++) {
            String day = LocalDate.now().minusDays(range - 1L - i).toString();
            byDay.put(day, new LinkedHashMap<>(Map.of(
                "date", day, "chatCount", 0L, "queryCount", 0L, "tokenCount", 0L)));
        }
        usageLogs.forEach(log -> {
            Map<String, Object> cell = byDay.get(log.getCreateTime().toLocalDate().toString());
            if (cell != null) {
                cell.put("chatCount", (long) cell.get("chatCount") + 1);
                cell.put("tokenCount", (long) cell.get("tokenCount")
                    + (log.getTotalTokens() == null ? 0L : log.getTotalTokens().longValue()));
            }
        });
        queryLogs.forEach(log -> {
            Map<String, Object> cell = byDay.get(log.getCreateTime().toLocalDate().toString());
            if (cell != null) {
                cell.put("queryCount", (long) cell.get("queryCount") + 1);
            }
        });
        return new ArrayList<>(byDay.values());
    }

    @Override
    public List<Map<String, Object>> hotQueries(int limit) {
        Long tenantId = currentTenantId();
        int topN = limit > 0 && limit <= 50 ? limit : 10;
        List<AiQueryLog> logs = queryLogMapper.selectList(
            new LambdaQueryWrapper<AiQueryLog>().eq(AiQueryLog::getTenantId, tenantId));
        Map<String, Long> grouped = logs.stream()
            .collect(Collectors.groupingBy(
                l -> normalizeQuery(l.getQuery()),
                Collectors.counting()));
        return grouped.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(topN)
            .map(e -> Map.<String, Object>of("query", e.getKey(), "count", e.getValue()))
            .toList();
    }

    @Override
    public List<Map<String, Object>> kbDocDistribution() {
        Long tenantId = currentTenantId();
        return kbMapper.selectList(
            new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getTenantId, tenantId)
                .orderByDesc(AiKnowledgeBase::getDocCount))
            .stream()
            .map(kb -> Map.<String, Object>of(
                "name", kb.getName(),
                "docCount", kb.getDocCount() != null ? kb.getDocCount() : 0))
            .toList();
    }

    /** 热词归一化：去首尾空白、折叠空白、截断，避免换行/超长污染统计 */
    private static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        String trimmed = query.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}
