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
import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.security.platform.PlatformUserChecker;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Set;
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
public class SysPermissionServiceImpl implements SysPermissionService, PlatformUserChecker {

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserMapper userMapper;
    private final SysAuthTemplateService authTemplateService;

    @Override
    public List<String> listPermissions(Long userId) {
        return TenantContext.executeIgnore(() -> {
            if (isSuperAdminInternal(userId)) {
                return List.of(AdminConstants.ALL_PERMISSION);
            }
            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                return List.of();
            }
            boolean platformUser = AdminConstants.USER_TYPE_PLATFORM.equals(user.getUserType());
            Set<Long> allowedMenuIds = platformUser ? Set.of()
                : authTemplateService.resolveTenantMenuIds(user.getTenantId());
            return menuMapper.selectByUserId(userId).stream()
                .filter(menu -> platformUser || allowedMenuIds.contains(menu.getId()))
                .filter(menu -> platformUser || !Boolean.TRUE.equals(menu.getPlatformOnly()))
                .map(SysMenu::getAuthCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
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
    public boolean isPlatformUser(Long userId) {
        return TenantContext.executeIgnore(() -> userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getId, userId)
            .eq(SysUser::getUserType, "PLATFORM")
            .eq(SysUser::getStatus, EntityStatus.ENABLED.getCode())
            .eq(SysUser::getIsDeleted, 0)) > 0);
    }

    @Override
    public boolean isSuperAdmin(Long userId) {
        return TenantContext.executeIgnore(() -> isSuperAdminInternal(userId));
    }

    /**
     * 内部超管判定，调用方需已处于租户忽略上下文中。
     */
    private boolean isSuperAdminInternal(Long userId) {
        return roleMapper.countPlatformSuperByUserId(userId) > 0;
    }
}
