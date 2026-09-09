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

import cn.ypbin.admin.ai.entity.AiQueryLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 检索问答日志 Mapper。
 *
 * @author wenbin
 * @since 2026-08-18
 */
public interface AiQueryLogMapper extends BaseMapper<AiQueryLog> {

    /**
     * 统计租户某时间窗口内按天聚合的问答条数（SQL 聚合，避免窗口内全量行拉取）。
     *
     * @param tenantId 租户 ID
     * @param from     起始时间（含）
     * @param to       截止时间（含，与原有 between 口径一致）
     * @return 每天一行（statDate/queryCount），按日期升序
     */
    @Select("""
        SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate,
               COUNT(*) AS queryCount
        FROM ai_query_log
        WHERE tenant_id = #{tenantId} AND create_time BETWEEN #{from} AND #{to}
        GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
        ORDER BY statDate
        """)
    List<Map<String, Object>> selectDailyCountByTenant(@Param("tenantId") Long tenantId,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);

    /**
     * 查询租户检索热词 Top N（SQL 层归一化问题文本后分组计数，避免全表拉取后在 JVM 聚合）。
     *
     * <p>归一化口径与内存实现保持一致：去首尾空白、连续空白折叠为单空格、超长截断至 100 字符。</p>
     *
     * @param tenantId 租户 ID
     * @param topN     返回条数上限
     * @return 每热词一行（normQuery/queryCount），按次数降序
     */
    @Select("""
        SELECT normQuery, COUNT(*) AS queryCount
        FROM (
            SELECT COALESCE(SUBSTRING(TRIM(REGEXP_REPLACE(TRIM(query), '[[:space:]]+', ' ')), 1, 100), '')
                   AS normQuery
            FROM ai_query_log
            WHERE tenant_id = #{tenantId}
        ) q
        GROUP BY normQuery
        ORDER BY queryCount DESC, normQuery
        LIMIT #{topN}
        """)
    List<Map<String, Object>> selectHotQueries(@Param("tenantId") Long tenantId,
                                               @Param("topN") int topN);
}
