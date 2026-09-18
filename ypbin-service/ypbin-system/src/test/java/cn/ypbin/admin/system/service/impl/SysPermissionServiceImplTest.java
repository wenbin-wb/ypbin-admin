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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.starter.data.core.EntityStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 权限码判定源测试：权限码唯一来源是 {@code sys_role_menu} → {@code sys_menu.auth_code}。
 *
 * <p>本测试锁定两条「一改就出事故」的语义：</p>
 * <ol>
 *   <li><b>平台超管短路返回 {@code *:*:*} 且不查用户/菜单</b>——这是「超管不掉权限」的唯一保障。
 *       若有人把短路删掉改成「按菜单逐条算」，超管只要有一条菜单没勾就会掉权限。</li>
 *   <li><b>租户用户只拿到「自己的菜单 ∩ 租户权限模板 ∩ 非平台专用」</b>，且去重、剔除空权限码。</li>
 * </ol>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class SysPermissionServiceImplTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysAuthTemplateService authTemplateService = mock(SysAuthTemplateService.class);

    private final SysPermissionServiceImpl permissionService =
        new SysPermissionServiceImpl(roleMapper, menuMapper, userMapper, authTemplateService);

    @Test
    @DisplayName("超管：短路返回 *:*:*，且不查询用户与菜单（超管不掉权限的唯一保障）")
    void superAdminShouldShortCircuitWithAllPermission() {
        when(roleMapper.countPlatformSuperByUserId(1L)).thenReturn(1L);

        List<String> permissions = permissionService.listPermissions(1L);

        assertThat(permissions).containsExactly(AdminConstants.ALL_PERMISSION);
        // 短路必须是「不查库就返回」，否则超管的权限就取决于菜单勾了多少
        verify(roleMapper).countPlatformSuperByUserId(1L);
        verifyNoMoreInteractions(roleMapper);
        verifyNoMoreInteractions(userMapper);
        verifyNoMoreInteractions(menuMapper);
    }

    @Test
    @DisplayName("普通租户用户：只拿到模板内、非平台专用的菜单权限码，去重且剔除空码")
    void tenantUserShouldGetMenuAuthCodes() {
        when(roleMapper.countPlatformSuperByUserId(2L)).thenReturn(0L);
        when(userMapper.selectById(2L)).thenReturn(tenantUser(2L, 9L));
        when(authTemplateService.resolveTenantMenuIds(9L)).thenReturn(Set.of(10L, 11L));
        when(menuMapper.selectByUserId(2L)).thenReturn(List.of(
            menu(10L, "system:user:list", false),
            menu(11L, "system:user:add", false),
            menu(12L, "system:menu:list", false),
            menu(13L, "system:platform:only", true),
            menu(10L, "system:user:list", false),
            menu(14L, "  ", false),
            menu(15L, null, false)));

        List<String> permissions = permissionService.listPermissions(2L);

        assertThat(permissions).containsExactly("system:user:list", "system:user:add");
    }

    @Test
    @DisplayName("禁用用户：即使有菜单授权也返回空权限（拒绝）")
    void disabledUserShouldGetNothing() {
        when(roleMapper.countPlatformSuperByUserId(3L)).thenReturn(0L);
        SysUser disabled = tenantUser(3L, 9L);
        disabled.setStatus(EntityStatus.DISABLED.getCode());
        when(userMapper.selectById(3L)).thenReturn(disabled);

        assertThat(permissionService.listPermissions(3L)).isEmpty();
        verifyNoMoreInteractions(menuMapper);
    }

    @Test
    @DisplayName("角色码：只返回非空角色标识")
    void roleCodesShouldSkipBlank() {
        SysRole admin = new SysRole();
        admin.setCode("tenant_admin");
        SysRole blank = new SysRole();
        blank.setCode("   ");
        SysRole nullCode = new SysRole();
        when(roleMapper.selectByUserId(2L)).thenReturn(List.of(admin, blank, nullCode));

        assertThat(permissionService.listRoleCodes(2L)).containsExactly("tenant_admin");
    }

    private SysUser tenantUser(Long id, Long tenantId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUserType(AdminConstants.USER_TYPE_TENANT);
        user.setStatus(EntityStatus.ENABLED.getCode());
        return user;
    }

    private SysMenu menu(Long id, String authCode, boolean platformOnly) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setAuthCode(authCode);
        menu.setPlatformOnly(platformOnly);
        return menu;
    }
}
