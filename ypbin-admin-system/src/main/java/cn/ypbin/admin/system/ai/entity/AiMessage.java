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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 消息记录（展示用，AI Memory 独立持久化）。
 *
 * <p>不继承 BaseEntity：无逻辑删除、无 update_user / update_time / status 字段。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
@TableName("ai_message")
public class AiMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，雪花 ID */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 租户 ID */
    private Integer tenantId;

    /** 会话 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 角色：user | assistant */
    private String role;

    /** 消息内容（Markdown） */
    private String content;

    /** Token 消耗 */
    private Integer tokens;

    /** 创建时间 */
    private LocalDateTime createTime;
}
