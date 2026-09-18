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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.model.req.RoleSaveReq;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 角色授权变更后的权限缓存失效测试（回归）。
 *
 * <p>背景：{@code SysCache} 的用户权限缓存是<b>永久缓存</b>（TTL 传 null，键 {@code sys:perm:user:<id>}），
 * 一致性完全依赖写路径主动失效。历史缺陷正是「改了角色勾选的菜单，用户的权限缓存没人清」——
 * 表现为「授权改了但功能一直不生效」。本测试锁定：改角色（含勾选菜单）后，必须清除该角色下
 * <b>全部</b>用户的角色/权限缓存，且是<b>精确失效</b>（只清受影响用户，不做整体清）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class SysRoleServiceImplPermissionCacheTest {

    private static final long ROLE_ID = 9L;
    private static final long TENANT_ID = 100L;

    private final SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
    private final SysRoleDeptMapper roleDeptMapper = mock(SysRoleDeptMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysAuthTemplateService authTemplateService = mock(SysAuthTemplateService.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);

    private SysRoleServiceImpl roleService;

    /**
     * MyBatis-Plus 的 Lambda 条件构造器需要实体的 TableInfo 缓存；单测不启动 Spring，
     * 故按 {@code TrackAggregateSqlMappingTest} 的既有做法显式初始化，
     * 否则会抛 {@code can not find lambda cache for this entity}。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "permission-cache"), SysMenu.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "permission-cache"), SysRole.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "permission-cache"), SysUserRole.class);
    }

    @BeforeEach
    void setUp() {
        roleService = new SysRoleServiceImpl(roleMenuMapper, roleDeptMapper, userRoleMapper,
            menuMapper, deptMapper, authTemplateService);
        // ServiceImpl 的 baseMapper 由容器注入，单测里手工装配（不启动 Spring）
        ReflectionTestUtils.setField(roleService, "baseMapper", roleMapper);
    }

    @Test
    @DisplayName("改角色勾选的菜单后：清除该角色下全部用户的角色/权限缓存（精确失效，逐个去重）")
    void updateRoleShouldEvictAllRoleUsersPermissionCache() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(tenantRole());
        when(roleMapper.exists(any())).thenReturn(false);
        when(menuMapper.selectList(any())).thenReturn(List.of(enabledMenu(10L), enabledMenu(11L)));
        when(authTemplateService.resolveTenantMenuIds(TENANT_ID)).thenReturn(Set.of(10L, 11L));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
            new SysUserRole(7L, ROLE_ID),
            new SysUserRole(8L, ROLE_ID),
            new SysUserRole(7L, ROLE_ID)));

        try (MockedStatic<SysCache> sysCache = mockStatic(SysCache.class);
            MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            roleService.updateRole(ROLE_ID, roleReqWithMenus(List.of(10L, 11L)));

            // 去重后的受影响用户，一次性批量失效（不是逐个用户一次缓存往返）
            sysCache.verify(() -> SysCache.evictUserAuth(List.of(7L, 8L)));
        }
    }

    @Test
    @DisplayName("角色下没有人：不发起缓存删除（空集合短路，不做无谓的缓存往返）")
    void updateRoleShouldNotEvictWhenNoUserHoldsRole() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(tenantRole());
        when(roleMapper.exists(any())).thenReturn(false);
        when(menuMapper.selectList(any())).thenReturn(List.of(enabledMenu(10L), enabledMenu(11L)));
        when(authTemplateService.resolveTenantMenuIds(TENANT_ID)).thenReturn(Set.of(10L, 11L));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<SysCache> sysCache = mockStatic(SysCache.class);
            MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            roleService.updateRole(ROLE_ID, roleReqWithMenus(List.of(10L, 11L)));

            sysCache.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("改角色状态：同样清除该角色下用户的权限缓存（禁用角色即掉权限）")
    void updateStatusShouldEvictRoleUsersPermissionCache() {
        when(roleMapper.selectById(ROLE_ID)).thenReturn(tenantRole());
        when(roleMapper.update(any(), any())).thenReturn(1);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(new SysUserRole(7L, ROLE_ID)));

        try (MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {
            roleService.updateStatus(ROLE_ID, EntityStatus.DISABLED.getCode());

            sysCache.verify(() -> SysCache.evictUserAuth(List.of(7L)));
        }
    }

    private SysRole tenantRole() {
        SysRole role = new SysRole();
        role.setId(ROLE_ID);
        role.setCode("tenant_op");
        role.setRoleType(AdminConstants.ROLE_TYPE_TENANT);
        return role;
    }

    private SysMenu enabledMenu(Long id) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setStatus(EntityStatus.ENABLED.getCode());
        return menu;
    }

    private RoleSaveReq roleReqWithMenus(List<Long> menuIds) {
        RoleSaveReq req = new RoleSaveReq();
        req.setName("运营");
        req.setCode("tenant_op");
        req.setDataScope(1);
        req.setPermissions(menuIds);
        req.setDeptIds(List.of());
        return req;
    }
}
