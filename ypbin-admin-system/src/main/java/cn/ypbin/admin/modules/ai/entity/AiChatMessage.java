/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.entity;

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 对话消息实体。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
@TableName("ai_chat_message")
public class AiChatMessage extends TenantBaseEntity {

    /** 会话 ID */
    private Long sessionId;

    /** 用户 ID */
    private Long userId;

    /** 父消息 ID（支持分支对话） */
    private Long parentId;

    /** 角色（user/assistant/system/tool） */
    private String role;

    /** 消息内容 */
    private String content;

    /** token 消耗（assistant 消息记录） */
    private Integer tokens;

    /** 使用的模型名称（assistant 消息记录） */
    private String modelName;

    /** 结束原因（stop/length/tool_calls） */
    private String finishReason;

    /** 工具调用记录（JSON 数组） */
    private String toolCalls;

    /** 图片附件（多模态输入，JSON 数组） */
    private String images;

    /** 扩展元数据（latency_ms、temperature 等） */
    private String metadata;
}
