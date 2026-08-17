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

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 对话消息实体（append-only，简化自含字段以匹配消息表结构）。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
@TableName("ai_chat_message")
public class AiChatMessage implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 租户 ID */
    @TableField("tenant_id")
    private Long tenantId;

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

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 逻辑删除标记 */
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
