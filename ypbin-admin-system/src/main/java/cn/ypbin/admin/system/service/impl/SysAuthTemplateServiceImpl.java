/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.entity.SysAuthTemplate;
import cn.ypbin.admin.system.entity.SysTemplateMenu;
import cn.ypbin.admin.system.entity.SysTenant;
import cn.ypbin.admin.system.mapper.SysAuthTemplateMapper;
import cn.ypbin.admin.system.mapper.SysTemplateMenuMapper;
import cn.ypbin.admin.system.mapper.SysTenantMapper;
import cn.ypbin.admin.system.model.req.AuthTemplateSaveReq;
import cn.ypbin.admin.system.model.resp.AuthTemplateResp;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限模板服务实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SysAuthTemplateServiceImpl extends BaseServiceImpl<SysAuthTemplateMapper, SysAuthTemplate>
    implements SysAuthTemplateService {

    private final SysTemplateMenuMapper templateMenuMapper;
    private final SysTenantMapper tenantMapper;

    @Override
    public List<AuthTemplateResp> listTemplates() {
        return list().stream().map(t -> {
            AuthTemplateResp resp = new AuthTemplateResp();
            BeanUtils.copyProperties(t, resp);
            resp.setMenuIds(templateMenuMapper.selectMenuIdsByTemplateId(t.getId()));
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTemplate(AuthTemplateSaveReq req) {
        checkCodeUnique(req.getCode(), null);
        SysAuthTemplate template = new SysAuthTemplate();
        BeanUtils.copyProperties(req, template, "menuIds");
        save(template);
        assignMenus(template.getId(), req.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(Long id, AuthTemplateSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("权限模板不存在");
        }
        checkCodeUnique(req.getCode(), id);
        SysAuthTemplate template = new SysAuthTemplate();
        BeanUtils.copyProperties(req, template, "menuIds");
        template.setId(id);
        updateById(template);
        templateMenuMapper.delete(new LambdaQueryWrapper<SysTemplateMenu>()
            .eq(SysTemplateMenu::getTemplateId, id));
        assignMenus(id, req.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        removeById(id);
        templateMenuMapper.delete(new LambdaQueryWrapper<SysTemplateMenu>()
            .eq(SysTemplateMenu::getTemplateId, id));
    }

    @Override
    public Set<Long> listMenuIds(Long templateId) {
        List<Long> ids = templateMenuMapper.selectMenuIdsByTemplateId(templateId);
        return ids == null ? Set.of() : new HashSet<>(ids);
    }

    @Override
    public Set<Long> resolveTenantMenuIds(Long tenantId) {
        if (tenantId == null) {
            return null; // 平台视角：不过滤
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getTemplateId() == null) {
            return null; // 租户未配置模板：不过滤（兼容现状）
        }
        return listMenuIds(tenant.getTemplateId());
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysAuthTemplate>()
            .eq(SysAuthTemplate::getCode, code)
            .ne(excludeId != null, SysAuthTemplate::getId, excludeId));
        if (exists) {
            throw new BusinessException("模板编码已存在：" + code);
        }
    }

    private void assignMenus(Long templateId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds.stream().distinct().collect(Collectors.toList())) {
            templateMenuMapper.insert(new SysTemplateMenu(templateId, menuId));
        }
    }
}
