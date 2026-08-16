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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 对话角色实体。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
@TableName("ai_chat_role")
public class AiChatRole extends TenantBaseEntity {

    /** 角色名称 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 角色头像 URL */
    private String avatar;

    /** 系统提示词 */
    private String systemPrompt;

    /** 分类（assistant/translator/coder/analyst/writer） */
    private String category;

    /** 推荐模型 */
    private String modelPreference;

    /** 默认温度参数 */
    private BigDecimal temperature;

    /** 是否内置（0 否 1 是） */
    private Integer isBuiltin;

    /** 排序 */
    private Integer sort;
}
