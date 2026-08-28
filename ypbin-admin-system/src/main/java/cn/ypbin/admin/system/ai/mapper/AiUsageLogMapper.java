/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.mapper;

import cn.ypbin.admin.system.ai.entity.AiUsageLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}
