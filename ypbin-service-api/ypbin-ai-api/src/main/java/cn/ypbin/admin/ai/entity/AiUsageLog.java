/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.entity;

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * AI Token 用量日志。
 *
 * <p><b>NULL 语义（与 starter 的 {@code AiUsageInfo} 契约一致，禁止折算成 0）</b>：</p>
 * <ul>
 *   <li>{@code inputTokens}/{@code outputTokens}/{@code totalTokens} 为 {@code null} 表示
 *       <b>上游未回报用量</b>（流式用量通常只在最后分片回报；框架仅在 {@code streamOptions == null} 时
 *       才默认请求 {@code stream_options.include_usage=true}）。0 与「真实 0 token」不可区分，
 *       因此未知一律落 NULL，统计侧 SUM/AVG 天然跳过 NULL，绝不按 0 计入。</li>
 *   <li>{@code userId} 为 {@code null} 表示该入口没有用户上下文（分享页/挂件/知识库检索问答为匿名调用）。</li>
 *   <li>{@code conversationId} 为 {@code null} 表示该入口没有会话实体
 *       （匿名入口传入的是 {@code share-<kbId>} 等非会话标识，不伪造 ID）。</li>
 *   <li>{@code errorMessage} 仅在 {@code outcome = failure} 时有值。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
@TableName("ai_usage_log")
public class AiUsageLog extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID（匿名入口为 null） */
    private Long userId;

    /** 会话 ID（非会话入口为 null） */
    private Long conversationId;

    /** 模型配置 ID */
    private Long modelId;

    /** 模型名称（冗余，防改名影响统计） */
    private String modelName;

    /** 输入 Token（null = 上游未回报） */
    private Integer inputTokens;

    /** 输出 Token（null = 上游未回报） */
    private Integer outputTokens;

    /** 合计 Token（null = 上游未回报） */
    private Integer totalTokens;

    /** 响应耗时 ms */
    private Long latencyMs;

    /** 终局结果：success 成功 | failure 失败 | cancelled 已取消（存 {@code AiUsageOutcome#code()}） */
    private String outcome;

    /** 失败原因摘要（成功与取消为 null） */
    private String errorMessage;
}
