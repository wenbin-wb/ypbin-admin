/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.service;

import java.util.List;
import java.util.Map;

/**
 * AI 统计看板服务。
 *
 * @author wenbin
 * @since 2026-08-18
 */
public interface AiStatsService {

    /**
     * 概览统计：知识库数、文档总数、问答次数、检索次数、Token 总量。
     *
     * @return 概览映射（kbCount/docTotal/chatCount/queryCount/tokenTotal）
     */
    Map<String, Object> summary();

    /**
     * 近 N 天每日问答/检索/Token 趋势。
     *
     * @param days 天数（默认 30）
     * @return 每天一条（date/chatCount/queryCount/tokenCount），缺失日期补 0
     */
    List<Map<String, Object>> daily(int days);

    /**
     * 搜索热词 Top N（按问题原文分组计数）。
     *
     * @param limit 条数上限（默认 10）
     * @return 热词列表（query/count）
     */
    List<Map<String, Object>> hotQueries(int limit);

    /**
     * 各知识库文档数分布。
     *
     * @return 列表（name/docCount）
     */
    List<Map<String, Object>> kbDocDistribution();
}
