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

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.system.entity.SysAuthTemplate;
import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.entity.SysTemplateMenu;
import cn.ypbin.admin.system.entity.SysTenant;
import cn.ypbin.admin.system.mapper.SysAuthTemplateMapper;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
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
 * @since 2026-08-09
 */
@Service
@RequiredArgsConstructor
public class SysAuthTemplateServiceImpl extends BaseServiceImpl<SysAuthTemplateMapper, SysAuthTemplate>
    implements SysAuthTemplateService {

    private final SysTemplateMenuMapper templateMenuMapper;
    private final SysTenantMapper tenantMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public List<AuthTemplateResp> listTemplates() {
        return list().stream().map(template -> {
            AuthTemplateResp resp = new AuthTemplateResp();
            BeanUtils.copyProperties(template, resp);
            resp.setMenuIds(List.copyOf(listMenuIds(template.getId())));
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTemplate(AuthTemplateSaveReq req) {
        validateMenuIds(req.getMenuIds());
        checkCodeUnique(req.getCode(), null);
        SysAuthTemplate template = new SysAuthTemplate();
        BeanUtils.copyProperties(req, template, "menuIds");
        if (!save(template)) {
            throw new BusinessException("新增权限模板失败");
        }
        assignMenus(template.getId(), req.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(Long id, AuthTemplateSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("权限模板不存在");
        }
        validateMenuIds(req.getMenuIds());
        checkCodeUnique(req.getCode(), id);
        SysAuthTemplate template = new SysAuthTemplate();
        BeanUtils.copyProperties(req, template, "menuIds");
        template.setId(id);
        if (!updateById(template)) {
            throw new BusinessException("修改权限模板失败");
        }
        templateMenuMapper.delete(new LambdaQueryWrapper<SysTemplateMenu>()
            .eq(SysTemplateMenu::getTemplateId, id));
        assignMenus(id, req.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        if (!removeById(id)) {
            throw new BusinessException("删除权限模板失败");
        }
        templateMenuMapper.delete(new LambdaQueryWrapper<SysTemplateMenu>()
            .eq(SysTemplateMenu::getTemplateId, id));
    }

    @Override
    public Set<Long> listMenuIds(Long templateId) {
        List<Long> ids = templateMenuMapper.selectMenuIdsByTemplateId(templateId);
        return ids == null ? Set.of() : resolveAvailableMenuIds(new HashSet<>(ids));
    }

    @Override
    public Set<Long> resolveTenantMenuIds(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException("无法确定当前租户");
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1 || Boolean.TRUE.equals(tenant.getIsDeleted())) {
            throw new BusinessException("当前租户不存在或已禁用");
        }
        if (tenant.getTemplateId() == null) {
            throw new BusinessException("当前租户未配置权限模板");
        }
        SysAuthTemplate template = getById(tenant.getTemplateId());
        if (template == null || template.getStatus() == null || template.getStatus() != 1
            || Boolean.TRUE.equals(template.getIsDeleted())) {
            throw new BusinessException("当前租户的权限模板不存在或已禁用");
        }
        return resolveAvailableMenuIds(listMenuIds(template.getId()));
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysAuthTemplate>()
            .eq(SysAuthTemplate::getCode, code)
            .ne(excludeId != null, SysAuthTemplate::getId, excludeId));
        if (exists) {
            throw new BusinessException("模板编码已存在：" + code);
        }
    }

    private void validateMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        if (menuIds.stream().anyMatch(menuId -> menuId == null || menuId <= 0)) {
            throw new BusinessException("权限模板菜单 ID 必须为正数");
        }
        Set<Long> requestedIds = new HashSet<>(menuIds);
        Set<Long> availableIds = resolveAvailableMenuIds(requestedIds);
        if (!availableIds.equals(requestedIds)) {
            throw new BusinessException("权限模板包含不存在、已禁用或平台专用的菜单");
        }
    }

    private Set<Long> resolveAvailableMenuIds(Set<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return Set.of();
        }
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
            .in(SysMenu::getId, menuIds)
            .eq(SysMenu::getStatus, EntityStatus.ENABLED.getCode())
            .eq(SysMenu::getIsDeleted, 0)
            .eq(SysMenu::getPlatformOnly, false))
            .stream().map(SysMenu::getId).collect(Collectors.toSet());
    }

    private void assignMenus(Long templateId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : new HashSet<>(menuIds)) {
            templateMenuMapper.insert(new SysTemplateMenu(templateId, menuId));
        }
    }
}
