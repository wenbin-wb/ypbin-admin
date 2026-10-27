/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service.impl;

import cn.ypbin.admin.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.ai.service.AiWidgetService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiPublicAccessSupport publicAccessSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String setWidgetEnabled(Long knowledgeBaseId, boolean enabled) {
        AiKnowledgeBase kb = kbMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        // 用 UpdateWrapper 显式 set（含 null），否则 MyBatis-Plus 默认字段策略会忽略
        // null 字段，导致停用时令牌不被清除（残留旧值）。
        LambdaUpdateWrapper<AiKnowledgeBase> uw = new LambdaUpdateWrapper<>();
        uw.eq(AiKnowledgeBase::getId, knowledgeBaseId);
        String token = null;
        if (enabled) {
            token = publicAccessSupport.generateToken();
            uw.set(AiKnowledgeBase::getWidgetToken, token);
            uw.set(AiKnowledgeBase::getWidgetEnabled, 1);
        } else {
            uw.set(AiKnowledgeBase::getWidgetToken, null);
            uw.set(AiKnowledgeBase::getWidgetEnabled, 0);
        }
        uw.set(AiKnowledgeBase::getUpdateTime, LocalDateTime.now());
        if (kbMapper.update(null, uw) != 1) {
            throw new BusinessException("挂件状态保存失败");
        }
        return token;
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
        return publicAccessSupport.answer(kb, "widget-" + kb.getId(), question, "挂件");
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
}
