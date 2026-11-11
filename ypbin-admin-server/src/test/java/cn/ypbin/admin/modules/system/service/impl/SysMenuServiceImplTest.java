/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.modules.system.entity.SysMenu;
import cn.ypbin.admin.modules.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.modules.system.model.resp.MenuResp;
import cn.ypbin.admin.modules.system.service.SysAuthTemplateService;
import cn.ypbin.admin.modules.system.service.SysPermissionService;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link SysMenuServiceImpl} 菜单管理响应测试。
 *
 * @author wenbin
 * @since 2026-08-10
 */
class SysMenuServiceImplTest {

    private SysMenuServiceImpl service;

    private SysAuthTemplateService authTemplateService;

    @BeforeEach
    void setUp() {
        authTemplateService = mock(SysAuthTemplateService.class);
        service = spy(new SysMenuServiceImpl(mock(SysPermissionService.class), mock(SysRoleMenuMapper.class),
            authTemplateService));
    }

    @Test
    void menuTreeKeepsHierarchyAndPlatformOnly() {
        doReturn(List.of(menu(1L, 0L, "SystemRoot", true), menu(2L, 1L, "SystemChild", false)))
            .when(service).list(any(LambdaQueryWrapper.class));

        List<MenuResp> tree = service.tree();

        assertThat(tree).hasSize(1);
        MenuResp root = tree.getFirst();
        assertThat(root.getId()).isEqualTo(1L);
        assertThat(root.getPlatformOnly()).isTrue();
        assertThat(root.getChildren()).hasSize(1);
        assertThat(root.getChildren().getFirst().getId()).isEqualTo(2L);
        assertThat(root.getChildren().getFirst().getPlatformOnly()).isFalse();
    }

    /**
     * 模板外的祖先只为保持树结构保留，必须标记为不可授权，否则前端“全选”会提交被后端拒绝的 id。
     */
    @Test
    void assignableTreeMarksAncestorOutsideTemplateAsUnassignable() {
        SysMenu root = menu(1L, 0L, "OrgRoot", true);
        SysMenu child = menu(2L, 1L, "UserMenu", false);
        SysMenu unrelated = menu(3L, 0L, "PlatformMenu", true);
        doReturn(List.of(root, child, unrelated)).when(service).list(any(LambdaQueryWrapper.class));
        when(authTemplateService.resolveTenantMenuIds(1L)).thenReturn(Set.of(2L));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getLoginUser).thenReturn(Optional.of(loginUser(1L)));

            List<MenuResp> tree = service.assignableTree();

            assertThat(tree).hasSize(1);
            MenuResp ancestor = tree.getFirst();
            assertThat(ancestor.getId()).isEqualTo(1L);
            assertThat(ancestor.getAssignable()).isFalse();
            assertThat(ancestor.getChildren()).hasSize(1);
            MenuResp kept = ancestor.getChildren().getFirst();
            assertThat(kept.getId()).isEqualTo(2L);
            assertThat(kept.getAssignable()).isTrue();
        }
    }

    /**
     * 父菜单被禁用时不能连坐：启用子菜单仍在模板内，树里必须还能看到并勾选。
     */
    @Test
    void assignableTreeKeepsChildrenWhenParentDisabled() {
        SysMenu disabledParent = menu(1L, 0L, "DisabledRoot", false);
        disabledParent.setStatus(0);
        SysMenu child = menu(2L, 1L, "UserMenu", false);
        doReturn(List.of(disabledParent, child)).when(service).list(any(LambdaQueryWrapper.class));
        when(authTemplateService.resolveTenantMenuIds(1L)).thenReturn(Set.of(2L));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getLoginUser).thenReturn(Optional.of(loginUser(1L)));

            List<MenuResp> tree = service.assignableTree();

            assertThat(tree).hasSize(1);
            assertThat(tree.getFirst().getAssignable()).isFalse();
            assertThat(tree.getFirst().getChildren()).hasSize(1);
            assertThat(tree.getFirst().getChildren().getFirst().getAssignable()).isTrue();
        }
    }

    private LoginUser loginUser(Long tenantId) {
        LoginUser loginUser = new LoginUser(1L, "tester");
        loginUser.setTenantId(tenantId);
        return loginUser;
    }

    private SysMenu menu(Long id, Long pid, String name, boolean platformOnly) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setPid(pid);
        menu.setName(name);
        menu.setType("menu");
        menu.setPlatformOnly(platformOnly);
        menu.setStatus(1);
        return menu;
    }
}
