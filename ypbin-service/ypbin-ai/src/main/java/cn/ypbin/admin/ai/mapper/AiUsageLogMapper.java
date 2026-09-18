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
 * <p><b>NULL 语义（贯穿本类所有聚合）</b>：三个 token 列为 {@code NULL} 表示「上游未回报用量」，
 * 与「真实 0 token」不可区分。因此：</p>
 * <ul>
 *   <li>聚合一律直接写 {@code SUM(total_tokens)} / {@code AVG(...)}，<b>不得</b>在聚合参数上套
 *       {@code COALESCE/IFNULL}——那会把未知行当成 0 参与求和与均值，等于把「未知」洗成「零用量」。
 *       SQL 的 {@code SUM}/{@code AVG} 天然跳过 NULL，语义正确。</li>
 *   <li>聚合结果为 {@code NULL} 表示「该分组没有任何已回报用量」。聚合层不再用
 *       {@code COALESCE(SUM(...), 0)} 把这种情况抹成 0；调用方（Service）为图表连续性把这层 NULL
 *       显示为 0，但<b>必须同时给出「未知条数」计数</b>（{@code unknownTokenCalls}/{@code unknownCalls}），
 *       使「无已回报用量」与「真实 0 用量」在响应里始终可区分。</li>
 *   <li>「未知条数」由 {@code COUNT(CASE WHEN total_tokens IS NULL THEN 1 END)} 得到（{@code COUNT}
 *       只数非 NULL，恒为数字，不会给出 NULL）。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiUsageLogMapper extends BaseMapper<AiUsageLog> {

    /**
     * 统计租户的对话数、已回报 Token 总量与用量未知的调用数（SQL 聚合，避免全表拉取）。
     *
     * @param tenantId 租户 ID
     * @return 含 chatCount / tokenTotal（{@code null} = 无任何已回报用量）/ unknownTokenCalls 的映射
     */
    @Select("""
        SELECT COUNT(*) AS chatCount,
               SUM(total_tokens) AS tokenTotal,
               COUNT(CASE WHEN total_tokens IS NULL THEN 1 END) AS unknownTokenCalls
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId} AND is_deleted = 0
        """)
    Map<String, Object> selectSummaryByTenant(@Param("tenantId") Long tenantId);

    /**
     * 统计租户某时间窗口内按天聚合的对话数、已回报 Token 总量与未知用量条数
     * （SQL 聚合，避免窗口内全量行拉取）。
     *
     * @param tenantId 租户 ID
     * @param from     起始时间（含）
     * @param to       截止时间（含，与原有 between 口径一致）
     * @return 每天一行（statDate/chatCount/tokenTotal/unknownCalls），按日期升序
     */
    @Select("""
        SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate,
               COUNT(*) AS chatCount,
               SUM(total_tokens) AS tokenTotal,
               COUNT(CASE WHEN total_tokens IS NULL THEN 1 END) AS unknownCalls
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId} AND create_time BETWEEN #{from} AND #{to}
          AND is_deleted = 0
        GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
        ORDER BY statDate
        """)
    List<Map<String, Object>> selectDailySummaryByTenant(@Param("tenantId") Long tenantId,
                                                         @Param("from") LocalDateTime from,
                                                         @Param("to") LocalDateTime to);

    /**
     * 统计租户某时间窗口内按天聚合的已回报 Token 总量与未知用量条数
     * （SQL 聚合，避免窗口内全量行拉取）。
     *
     * @param tenantId 租户 ID
     * @param from     起始时间（含）
     * @param to       截止时间（含，与原有 between 口径一致）
     * @return 每天一行（statDate/tokenTotal/unknownCalls），按日期升序
     */
    @Select("""
        SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate,
               SUM(total_tokens) AS tokenTotal,
               COUNT(CASE WHEN total_tokens IS NULL THEN 1 END) AS unknownCalls
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId} AND create_time BETWEEN #{from} AND #{to}
          AND is_deleted = 0
        GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
        ORDER BY statDate
        """)
    List<Map<String, Object>> selectDailyTokensByTenant(@Param("tenantId") Long tenantId,
                                                        @Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to);

    /**
     * 按模型聚合租户的已回报 Token 总量与未知用量条数（SQL 聚合，避免全表拉取）。
     *
     * @param tenantId 租户 ID
     * @return 每模型一行（modelName/tokenTotal/unknownCalls）；{@code modelName} 为 {@code null}
     *     表示上游未回报模型名
     */
    @Select("""
        SELECT model_name AS modelName,
               SUM(total_tokens) AS tokenTotal,
               COUNT(CASE WHEN total_tokens IS NULL THEN 1 END) AS unknownCalls
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId} AND is_deleted = 0
        GROUP BY model_name
        """)
    List<Map<String, Object>> selectTokensGroupByModel(@Param("tenantId") Long tenantId);

    /**
     * 统计租户用量总览：调用数、已回报 Token 总量、正耗时均值与三类终局结果分布
     * （SQL 聚合，避免全表拉取）。
     *
     * <p>三类终局结果的编码由调用方以参数传入（取自 {@code AiUsageOutcome#code()}），
     * 避免在 SQL 里硬编码枚举字面量。</p>
     *
     * @param tenantId      租户 ID
     * @param successCode   「成功」的落库编码
     * @param failureCode   「失败」的落库编码
     * @param cancelledCode 「已取消」的落库编码
     * @return 含 totalCalls / tokenTotal（{@code null} = 无任何已回报用量）/ unknownTokenCalls /
     *     successCalls / failureCalls / cancelledCalls / avgLatencyMs 的映射
     */
    @Select("""
        SELECT COUNT(*) AS totalCalls,
               SUM(total_tokens) AS tokenTotal,
               COUNT(CASE WHEN total_tokens IS NULL THEN 1 END) AS unknownTokenCalls,
               COUNT(CASE WHEN outcome = #{successCode} THEN 1 END) AS successCalls,
               COUNT(CASE WHEN outcome = #{failureCode} THEN 1 END) AS failureCalls,
               COUNT(CASE WHEN outcome = #{cancelledCode} THEN 1 END) AS cancelledCalls,
               COALESCE(AVG(CASE WHEN latency_ms > 0 THEN latency_ms END), 0) AS avgLatencyMs
        FROM ai_usage_log
        WHERE tenant_id = #{tenantId} AND is_deleted = 0
        """)
    Map<String, Object> selectSummaryStatsByTenant(@Param("tenantId") Long tenantId,
                                                   @Param("successCode") String successCode,
                                                   @Param("failureCode") String failureCode,
                                                   @Param("cancelledCode") String cancelledCode);
}
