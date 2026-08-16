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
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 对话会话。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
@TableName("ai_conversation")
public class AiConversation extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建用户 */
    private Long userId;

    /** 使用的模型配置 ID */
    private Long modelId;

    /** 会话标题（首条消息自动截取） */
    private String title;
}
