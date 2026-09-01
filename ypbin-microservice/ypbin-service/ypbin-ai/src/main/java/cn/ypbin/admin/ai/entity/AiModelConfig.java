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
 * AI 模型配置。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
@TableName("ai_model_config")
public class AiModelConfig extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模型显示名称 */
    private String name;

    /** 提供商：openai | deepseek | ollama | custom */
    private String provider;

    /**
     * 模型类型：{@code CHAT} 对话 | {@code EMBEDDING} 向量化。
     * embedding 模型页面化配置，替代 yml 硬编码。
     */
    private String modelType;

    /** API Key（AES-GCM 加密存储） */
    private String apiKey;

    /** 接口基础地址（Ollama / 自定义必填，openai 可留空用官方端点） */
    private String baseUrl;

    /** 模型名称，如 deepseek-v4-flash、gpt-5.6 */
    private String modelName;

    /** 是否默认模型 */
    private Integer isDefault;

    /** 备注 */
    private String remark;
}
