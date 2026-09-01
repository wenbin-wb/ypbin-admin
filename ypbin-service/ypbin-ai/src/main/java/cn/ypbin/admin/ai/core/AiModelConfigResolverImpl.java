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
import cn.ypbin.starter.ai.chat.AiModelConfigResolver;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
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
        // 优先登录上下文；匿名场景（如网页挂件）已由调用方显式绑定 TenantContext 租户，
        // 此处回退取之。两者皆无才失败，禁止静默回退默认租户。
        Long tenantId = UserContext.getTenantId()
            .or(() -> TenantContext.getTenantId())
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
        AiModelConfig config = modelConfigMapper.selectOne(
            new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getTenantId, tenantId)
                // 对话默认模型只取 CHAT 类型，避免 embedding 模型被误用为对话模型
                .eq(AiModelConfig::getModelType, "CHAT")
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
