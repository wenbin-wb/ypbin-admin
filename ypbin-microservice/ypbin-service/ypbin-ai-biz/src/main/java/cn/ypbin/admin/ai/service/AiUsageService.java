/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI Token 用量统计服务（平台级）。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiUsageService {

    /**
     * 按天聚合的 Token 用量（折线图数据）。
     *
     * @param startDate 开始日期（为空默认近 30 天）
     * @param endDate   结束日期（为空默认今天）
     * @return 每天的 totalTokens 合计
     */
    List<Map<String, Object>> dailyUsage(LocalDate startDate, LocalDate endDate);

    /**
     * 按模型聚合的 Token 用量（饼图数据）。
     *
     * @return 模型 Token 合计，按用量降序
     */
    List<Map<String, Object>> byModel();

    /**
     * 用量概览：总对话数、总 Token 数、平均响应耗时。
     *
     * @return 概览映射（totalCalls/totalTokens/avgLatencyMs）
     */
    Map<String, Object> summary();
}
