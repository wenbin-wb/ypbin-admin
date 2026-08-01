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

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 权限查询服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public List<String> listPermissions(Long userId) {
        return TenantContext.executeIgnore(() -> {
            if (isSuperAdminInternal(userId)) {
                return List.of(AdminConstants.ALL_PERMISSION);
            }
            return menuMapper.selectAuthCodesByUserId(userId);
        });
    }

    @Override
    public List<String> listRoleCodes(Long userId) {
        return TenantContext.executeIgnore(() ->
            roleMapper.selectByUserId(userId).stream()
                .map(SysRole::getCode)
                .filter(code -> code != null && !code.isBlank())
                .toList());
    }

    @Override
    public boolean isSuperAdmin(Long userId) {
        return TenantContext.executeIgnore(() -> isSuperAdminInternal(userId));
    }

    /**
     * 内部超管判定，调用方需已处于租户忽略上下文中。
     */
    private boolean isSuperAdminInternal(Long userId) {
        return roleMapper.selectByUserId(userId).stream()
            .anyMatch(role -> AdminConstants.SUPER_ADMIN_ROLE.equals(role.getCode()));
    }
}
