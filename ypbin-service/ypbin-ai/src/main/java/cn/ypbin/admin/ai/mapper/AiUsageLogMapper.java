/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.mapper;

import cn.ypbin.admin.ai.entity.AiUsageLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Token 用量日志 Mapper。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiUsageLogMapper extends BaseMapper<AiUsageLog> {

    /**
     * 统计租户的对话数与 Token 总量（SQL 聚合，避免全表拉取）。
     *
     * @param tenantId 租户 ID
     * @return 含 chatCount/tokenTotal 的映射
     */
    @Select("SELECT COUNT(*) AS chatCount, COALESCE(SUM(total_tokens), 0) AS tokenTotal "
        + "FROM ai_usage_log WHERE tenant_id = #{tenantId}")
    Map<String, Object> selectSummaryByTenant(@Param("tenantId") Long tenantId);

    /**
     * 统计租户某时间窗口内按天聚合的对话数与 Token 总量（SQL 聚合，避免窗口内全量行拉取）。
     *
     * @param tenantId 租户 ID
     * @param from     起始时间（含）
     * @param to       截止时间（含，与原有 between 口径一致）
     * @return 每天一行（statDate/chatCount/tokenTotal），按日期升序
     */
    @Select("""
        SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate,
               COUNT(*) AS chatCount,
               COALESCE(SUM(total_tokens), 0) AS tokenTotal
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId} AND create_time BETWEEN #{from} AND #{to}
        GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
        ORDER BY statDate
        """)
    List<Map<String, Object>> selectDailySummaryByTenant(@Param("tenantId") Long tenantId,
                                                         @Param("from") LocalDateTime from,
                                                         @Param("to") LocalDateTime to);

    /**
     * 统计租户某时间窗口内按天聚合的 Token 总量（SQL 聚合，避免窗口内全量行拉取）。
     *
     * @param tenantId 租户 ID
     * @param from     起始时间（含）
     * @param to       截止时间（含，与原有 between 口径一致）
     * @return 每天一行（statDate/tokenTotal），按日期升序
     */
    @Select("""
        SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate,
               COALESCE(SUM(total_tokens), 0) AS tokenTotal
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId} AND create_time BETWEEN #{from} AND #{to}
        GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
        ORDER BY statDate
        """)
    List<Map<String, Object>> selectDailyTokensByTenant(@Param("tenantId") Long tenantId,
                                                        @Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to);

    /**
     * 按模型聚合租户的 Token 总量（SQL 聚合，避免全表拉取）。
     *
     * @param tenantId 租户 ID
     * @return 每模型一行（modelName/tokenTotal）
     */
    @Select("""
        SELECT model_name AS modelName,
               COALESCE(SUM(total_tokens), 0) AS tokenTotal
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId}
        GROUP BY model_name
        """)
    List<Map<String, Object>> selectTokensGroupByModel(@Param("tenantId") Long tenantId);

    /**
     * 统计租户用量总览：调用数、Token 总量与正耗时均值（SQL 聚合，避免全表拉取）。
     *
     * @param tenantId 租户 ID
     * @return 含 totalCalls/tokenTotal/avgLatencyMs 的映射
     */
    @Select("""
        SELECT COUNT(*) AS totalCalls,
               COALESCE(SUM(total_tokens), 0) AS tokenTotal,
               COALESCE(AVG(CASE WHEN latency_ms > 0 THEN latency_ms END), 0) AS avgLatencyMs
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId}
        """)
    Map<String, Object> selectSummaryStatsByTenant(@Param("tenantId") Long tenantId);
}
