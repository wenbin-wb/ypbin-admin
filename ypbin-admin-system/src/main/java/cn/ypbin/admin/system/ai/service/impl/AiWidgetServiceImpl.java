/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service.impl;

import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.system.ai.service.AiWidgetService;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 网页挂件服务实现。
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Service
@RequiredArgsConstructor
public class AiWidgetServiceImpl implements AiWidgetService {

    private static final Logger log = LoggerFactory.getLogger(AiWidgetServiceImpl.class);

    private static final int TOKEN_BYTES = 16;

    private final AiKnowledgeBaseMapper kbMapper;
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String setWidgetEnabled(Long knowledgeBaseId, boolean enabled) {
        AiKnowledgeBase kb = kbMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        AiKnowledgeBase update = new AiKnowledgeBase();
        update.setId(knowledgeBaseId);
        if (enabled) {
            update.setWidgetToken(generateToken());
            update.setWidgetEnabled(1);
        } else {
            update.setWidgetToken(null);
            update.setWidgetEnabled(0);
        }
        update.setUpdateTime(LocalDateTime.now());
        kbMapper.updateById(update);
        return enabled ? update.getWidgetToken() : null;
    }

    @Override
    public Map<String, Object> getConfig(String token) {
        AiKnowledgeBase kb = requireEnabledKb(token);
        return Map.of("name", kb.getName(), "enabled", true,
            "department", kb.getTenantId() != null ? String.valueOf(kb.getTenantId()) : "");
    }

    @Override
    public String ask(String token, String question) {
        AiKnowledgeBase kb = requireEnabledKb(token);
        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService == null) {
            throw new BusinessException("AI 对话服务未配置，请先配置对话模型");
        }
        // 匿名请求无登录上下文：显式绑定知识库所属租户，保证 RAG 检索与 AI 调用在正确租户内
        List<String> tokens = TenantContext.executeWithTenant(kb.getTenantId(),
            () -> aiChatService.chatWithKnowledge(
                    "widget-" + kb.getId(), question, String.valueOf(kb.getId()))
                .collectList()
                .block());
        return tokens == null ? "" : String.join("", tokens);
    }

    /**
     * 按令牌查找已启用挂件的知识库。
     *
     * <p>匿名请求无租户上下文，MyBatis-Plus 租户插件会自动追加 {@code tenant_id=NULL}，
     * 导致查不到任何记录。这里先用 {@code executeIgnore} 临时忽略租户过滤，仅凭唯一的
     * {@code widgetToken} 反查知识库记录；查询结果随后在 {@code ask} 中通过
     * {@code TenantContext.executeWithTenant(kb.tenantId)} 显式绑定租户执行，不绕过隔离。</p>
     */
    private AiKnowledgeBase requireEnabledKb(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("无效的挂件令牌");
        }
        List<AiKnowledgeBase> list = TenantContext.executeIgnore(
            () -> kbMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeBase>()
                    .eq(AiKnowledgeBase::getWidgetToken, token)
                    .eq(AiKnowledgeBase::getWidgetEnabled, 1)
                    .last("LIMIT 1")));
        if (list.isEmpty()) {
            throw new BusinessException("挂件未启用或令牌无效");
        }
        return list.get(0);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
