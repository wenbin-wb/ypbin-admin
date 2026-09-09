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

import cn.ypbin.admin.ai.entity.AiDocument;
import cn.ypbin.admin.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.ai.entity.AiQueryLog;
import cn.ypbin.admin.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.ai.mapper.AiQueryLogMapper;
import cn.ypbin.admin.ai.mapper.AiUsageLogMapper;
import cn.ypbin.admin.ai.service.AiStatsService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(range - 1L).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        // SQL 层按天聚合用量与问答日志，避免窗口内全量行拉取后在 JVM 汇总
        List<Map<String, Object>> usageRows = usageLogMapper.selectDailySummaryByTenant(tenantId, from, to);
        List<Map<String, Object>> queryRows = queryLogMapper.selectDailyCountByTenant(tenantId, from, to);

        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        // 预填充缺失日期，保证趋势图连续
        for (int i = 0; i < range; i++) {
            String day = today.minusDays(range - 1L - i).toString();
            byDay.put(day, new LinkedHashMap<>(Map.of(
                "date", day, "chatCount", 0L, "queryCount", 0L, "tokenCount", 0L)));
        }
        for (Map<String, Object> row : usageRows) {
            Map<String, Object> cell = byDay.get(row.get("statDate"));
            if (cell != null) {
                cell.put("chatCount", (long) cell.get("chatCount")
                    + ((Number) row.get("chatCount")).longValue());
                cell.put("tokenCount", (long) cell.get("tokenCount")
                    + ((Number) row.get("tokenTotal")).longValue());
            }
        }
        for (Map<String, Object> row : queryRows) {
            Map<String, Object> cell = byDay.get(row.get("statDate"));
            if (cell != null) {
                cell.put("queryCount", (long) cell.get("queryCount")
                    + ((Number) row.get("queryCount")).longValue());
            }
        }
        return new ArrayList<>(byDay.values());
    }

    @Override
    public List<Map<String, Object>> hotQueries(int limit) {
        Long tenantId = currentTenantId();
        int topN = limit > 0 && limit <= 50 ? limit : 10;
        // SQL 层归一化问题文本（去首尾空白、折叠空白、截断 100 字符）后分组计数，避免全表拉取
        List<Map<String, Object>> rows = queryLogMapper.selectHotQueries(tenantId, topN);
        return rows.stream()
            .map(row -> Map.<String, Object>of(
                "query", row.get("normQuery"),
                "count", row.get("queryCount")))
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

    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}
