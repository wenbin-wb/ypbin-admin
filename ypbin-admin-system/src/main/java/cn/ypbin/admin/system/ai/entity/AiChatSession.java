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

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 对话会话实体。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
@TableName("ai_chat_session")
public class AiChatSession extends TenantBaseEntity {

    /** 会话标题 */
    private String title;

    /** 用户 ID */
    private Long userId;

    /** 绑定角色 ID（NULL 为默认助手） */
    private Long roleId;

    /** 使用的模型配置 ID */
    private Long modelId;

    /** 上下文窗口大小（保留最近 N 轮对话） */
    private Integer contextWindow;

    /** 累计消耗 token 数 */
    private Integer totalTokens;

    /** 消息总数 */
    private Integer messageCount;

    /** 是否置顶（0 否 1 是） */
    private Integer isPinned;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageAt;
}
