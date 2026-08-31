/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.core;

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.system.ai.entity.AiModelConfig;
import cn.ypbin.admin.system.ai.mapper.AiModelConfigMapper;
import cn.ypbin.starter.ai.chat.AiEmbeddingConfigResolver;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 动态向量化模型解析器：从 {@code ai_model_config} 表读取当前租户的默认 EMBEDDING 模型。
 *
 * <p>starter 的 {@code AiVectorStoreAutoConfiguration} 据此动态构建 OpenAI 兼容
 * EmbeddingModel 与 SimpleVectorStore，实现向量化模型的页面化配置。</p>
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
        // 请求线程有登录上下文时用当前租户；无上下文（启动装配/异步线程）回退平台默认租户 1
        Long tenantId = UserContext.getTenantId().orElse(1L);
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
