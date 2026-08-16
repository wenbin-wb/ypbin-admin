/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.ai.entity.AiUsageLog;
import cn.ypbin.admin.system.ai.mapper.AiUsageLogMapper;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Token 用量统计接口（平台级，仅授权角色可见）。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/ai/usage")
@RequiredArgsConstructor
public class AiUsageController extends BaseController {

    private final AiUsageLogMapper usageLogMapper;

    /**
     * 按天聚合的 Token 用量（折线图数据）。
     *
     * @param startDate 开始日期（默认近 30 天）
     * @param endDate   结束日期
     * @return 每天的 totalTokens 合计
     */
    @GetMapping("/daily")
    @SaCheckPermission("ai:usage:view")
    public R<List<Map<String, Object>>> dailyUsage(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        Integer tenantId = currentTenantId();
        LocalDateTime from = (startDate != null ? startDate : LocalDate.now().minusDays(29)).atStartOfDay();
        LocalDateTime to = (endDate != null ? endDate.plusDays(1) : LocalDate.now().plusDays(1)).atStartOfDay();
        List<AiUsageLog> logs = usageLogMapper.selectList(
            new LambdaQueryWrapper<AiUsageLog>()
                .eq(AiUsageLog::getTenantId, tenantId)
                .between(AiUsageLog::getCreateTime, from, to)
                .orderByAsc(AiUsageLog::getCreateTime));
        // 按日期聚合
        Map<String, Long> dailyMap = new LinkedHashMap<>();
        logs.forEach(log -> {
            String day = log.getCreateTime().toLocalDate().toString();
            dailyMap.merge(day, log.getTotalTokens() == null ? 0L : log.getTotalTokens().longValue(), Long::sum);
        });
        List<Map<String, Object>> result = dailyMap.entrySet().stream()
            .map(e -> Map.<String, Object>of("date", e.getKey(), "tokens", e.getValue()))
            .collect(Collectors.toList());
        return ok(result);
    }

    /**
     * 按模型聚合的 Token 用量（饼图数据）。
     */
    @GetMapping("/by-model")
    @SaCheckPermission("ai:usage:view")
    public R<List<Map<String, Object>>> byModel() {
        Integer tenantId = currentTenantId();
        List<AiUsageLog> logs = usageLogMapper.selectList(
            new LambdaQueryWrapper<AiUsageLog>()
                .eq(AiUsageLog::getTenantId, tenantId));
        Map<String, Long> grouped = logs.stream()
            .collect(Collectors.groupingBy(
                l -> l.getModelName() != null ? l.getModelName() : "未知",
                Collectors.summingLong(l -> l.getTotalTokens() == null ? 0L : l.getTotalTokens().longValue())));
        List<Map<String, Object>> result = grouped.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> Map.<String, Object>of("model", e.getKey(), "tokens", e.getValue()))
            .collect(Collectors.toList());
        return ok(result);
    }

    /**
     * 用量概览：总对话数、总 Token 数、平均响应耗时。
     */
    @GetMapping("/summary")
    @SaCheckPermission("ai:usage:view")
    public R<Map<String, Object>> summary() {
        Integer tenantId = currentTenantId();
        List<AiUsageLog> logs = usageLogMapper.selectList(
            new LambdaQueryWrapper<AiUsageLog>().eq(AiUsageLog::getTenantId, tenantId));
        long totalTokens = logs.stream()
            .mapToLong(l -> l.getTotalTokens() == null ? 0L : l.getTotalTokens().longValue()).sum();
        double avgLatency = logs.stream()
            .filter(l -> l.getLatencyMs() != null && l.getLatencyMs() > 0)
            .mapToLong(AiUsageLog::getLatencyMs).average().orElse(0);
        return ok(Map.of(
            "totalCalls", logs.size(),
            "totalTokens", totalTokens,
            "avgLatencyMs", Math.round(avgLatency)));
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Integer currentTenantId() {
        return UserContext.getTenantId()
            .map(Long::intValue)
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}
