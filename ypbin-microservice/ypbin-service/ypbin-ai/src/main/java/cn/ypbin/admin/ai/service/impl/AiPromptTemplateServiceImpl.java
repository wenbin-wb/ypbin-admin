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

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.ai.entity.AiPromptTemplate;
import cn.ypbin.admin.ai.mapper.AiPromptTemplateMapper;
import cn.ypbin.admin.ai.model.req.AiPromptTemplateSaveReq;
import cn.ypbin.admin.ai.service.AiPromptTemplateService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prompt 模板服务实现。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl implements AiPromptTemplateService {

    private final AiPromptTemplateMapper templateMapper;

    @Override
    public List<AiPromptTemplate> listTemplates() {
        Long tenantId = currentTenantId();
        return templateMapper.selectList(
            new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getTenantId, tenantId)
                .eq(AiPromptTemplate::getStatus, EntityStatus.ENABLED.getCode())
                .orderByDesc(AiPromptTemplate::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTemplate(AiPromptTemplateSaveReq req) {
        Long tenantId = currentTenantId();
        AiPromptTemplate tpl = new AiPromptTemplate();
        BeanUtils.copyProperties(req, tpl);
        tpl.setTenantId(tenantId);
        templateMapper.insert(tpl);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(Long id, AiPromptTemplateSaveReq req) {
        requireTemplate(id);
        AiPromptTemplate tpl = new AiPromptTemplate();
        BeanUtils.copyProperties(req, tpl, "id", "tenantId", "status");
        tpl.setId(id);
        templateMapper.updateById(tpl);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        requireTemplate(id);
        templateMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        AiPromptTemplate tpl = requireTemplate(id);
        tpl.setStatus(status);
        templateMapper.updateById(tpl);
    }

    /**
     * 查询模板并校验归属当前租户。
     *
     * @param id 模板 ID
     * @return 模板实体
     */
    private AiPromptTemplate requireTemplate(Long id) {
        AiPromptTemplate existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("模板不存在");
        }
        Long tenantId = currentTenantId();
        if (!tenantId.equals(existing.getTenantId())) {
            throw new BusinessException("无权操作其它租户的模板");
        }
        return existing;
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}
