/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * AI Token 用量日志。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
@TableName("ai_usage_log")
public class AiUsageLog extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Integer tenantId;

    /** 用户 ID */
    private Long userId;

    /** 会话 ID */
    private Long conversationId;

    /** 模型配置 ID */
    private Long modelId;

    /** 模型名称（冗余，防改名影响统计） */
    private String modelName;

    /** 输入 Token */
    private Integer inputTokens;

    /** 输出 Token */
    private Integer outputTokens;

    /** 合计 Token */
    private Integer totalTokens;

    /** 响应耗时 ms */
    private Long latencyMs;
}
