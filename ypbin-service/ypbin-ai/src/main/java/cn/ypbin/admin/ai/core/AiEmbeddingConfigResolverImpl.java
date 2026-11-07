/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.core;

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.ai.entity.AiModelConfig;
import cn.ypbin.admin.ai.mapper.AiModelConfigMapper;
import cn.ypbin.starter.ai.chat.AiEmbeddingConfigResolver;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 动态向量化模型解析器：从 {@code ai_model_config} 表读取当前租户的默认 EMBEDDING 模型。
 *
 * <p>starter 的 {@code AiVectorStoreAutoConfiguration} 据此动态构建 OpenAI 兼容
 * EmbeddingModel 与 SimpleVectorStore，实现向量化模型的页面化配置。</p>
 *
 * <p><strong>租户口径与对话侧一致（{@link AiModelConfigResolverImpl}）：</strong>先取登录上下文的租户，
 * 匿名链路（网页挂件/公开分享）由调用方显式绑定 {@code TenantContext} 租户，此处回退取之；
 * 两者皆无则抛 {@link BusinessException}，<b>不回退任何默认租户</b>——同一份模型配置在两条链路上必须
 * 解析到同一个租户，静默回退默认租户会让向量化与对话读到不同租户的模型（此前本类 {@code orElse(1L)}
 * 就存在该问题，属「禁静默降级」红线）。</p>
 *
 * @author wenbin
 * @since 2026-08-17
 */
@Component
@RequiredArgsConstructor
public class AiEmbeddingConfigResolverImpl implements AiEmbeddingConfigResolver {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiKeyCipher keyCipher;

    @Override
    public AiModelInfo resolve() {
        // 与 AiModelConfigResolverImpl 同口径：登录上下文 → 显式绑定的 TenantContext → 显式失败。
        // 禁止静默回退默认租户（那会让向量化与对话解析到不同租户的模型）。
        Long tenantId = UserContext.getTenantId()
            .or(TenantContext::getTenantId)
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
        AiModelConfig config = modelConfigMapper.selectOne(
            new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getTenantId, tenantId)
                // 向量化模型只取 EMBEDDING 类型
                .eq(AiModelConfig::getModelType, "EMBEDDING")
                .eq(AiModelConfig::getIsDefault, 1)
                .eq(AiModelConfig::getStatus, EntityStatus.ENABLED.getCode())
                .last("LIMIT 1"));
        if (config == null || config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            return null;
        }
        return new AiModelInfo(config.getBaseUrl(), keyCipher.decrypt(config.getApiKey()),
            config.getModelName());
    }
}
