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

import cn.ypbin.admin.system.ai.entity.AiModelConfig;
import cn.ypbin.admin.system.ai.mapper.AiModelConfigMapper;
import cn.ypbin.starter.ai.chat.AiModelConfigResolver;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 动态模型解析器：从 {@code ai_model_config} 表读取当前租户的默认启用模型。
 *
 * <p>starter 的 {@code DefaultAiChatService} 据此动态构建 OpenAI 兼容 ChatModel，
 * 无需在 yml 配置模型 starter；未配置默认模型时返回 {@code null}（对话回退 yml 模型路径）。</p>
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Component
@RequiredArgsConstructor
public class AiModelConfigResolverImpl implements AiModelConfigResolver {

    private final AiModelConfigMapper modelConfigMapper;
    private final AiKeyCipher keyCipher;

    @Override
    public AiModelInfo resolve() {
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);
        AiModelConfig config = modelConfigMapper.selectOne(
            new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getTenantId, tenantId)
                .eq(AiModelConfig::getIsDefault, 1)
                .eq(AiModelConfig::getStatus, 1)
                .last("LIMIT 1"));
        if (config == null || config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            return null;
        }
        return new AiModelInfo(config.getBaseUrl(), keyCipher.decrypt(config.getApiKey()),
            config.getModelName());
    }
}
