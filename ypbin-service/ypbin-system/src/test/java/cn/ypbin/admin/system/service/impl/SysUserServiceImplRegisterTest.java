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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.common.constant.RegisterConfigKeys;
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysTenant;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.mapper.SysPostMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.system.mapper.SysTenantMapper;
import cn.ypbin.admin.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserSocialMapper;
import cn.ypbin.admin.system.model.req.UserRegisterReq;
import cn.ypbin.admin.system.provider.AdminDataScopeHandler;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.service.SysSpaceRelationService;
import cn.ypbin.admin.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.security.online.OnlineUserService;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * 用户自助注册（system 落库侧）单元测试。
 *
 * <p>钉死四条纪律：① 开关关闭时不产生任何数据访问；② 归属租户只来自系统参数并校验可用，
 * 不接受调用方指定；③ 默认角色缺失/不可用时拒绝注册（不创建无角色用户）；④ 注册用户
 * 必须落在默认租户、启用状态、租户用户类型，并绑定默认角色 + 记录密码历史。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
class SysUserServiceImplRegisterTest {

    private static final Long TENANT_ID = 1L;

    private static final Long USER_ID = 99L;

    private static final Long ROLE_ID = 3L;

    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);

    private final SysConfigService configService = mock(SysConfigService.class);

    private final SysTenantMapper tenantMapper = mock(SysTenantMapper.class);

    private final SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);

    private final UserAccountSupport accountSupport = mock(UserAccountSupport.class);

    private SysUserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = spy(new SysUserServiceImpl(
            mock(SysSpaceRelationService.class),
            userRoleMapper,
            mock(SysUserPostMapper.class),
            mock(SysUserSocialMapper.class),
            roleMapper,
            mock(SysPostMapper.class),
            mock(OnlineUserService.class),
            mock(UserExcelComponent.class),
            accountSupport,
            mock(AdminDataScopeHandler.class),
            configService,
            tenantMapper,
            roleMenuMapper));
    }

    private UserRegisterReq request() {
        UserRegisterReq req = new UserRegisterReq();
        req.setUsername("alice");
        req.setPassword("Passw0rd!");
        req.setRealName("爱丽丝");
        return req;
    }

    private void stubSwitch(boolean enabled) {
        when(configService.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)).thenReturn(enabled);
    }

    private void stubTenantConfig(String value) {
        when(configService.getString(RegisterConfigKeys.REGISTER_DEFAULT_TENANT_ID, ""))
            .thenReturn(value);
    }

    private void stubRoleConfig(String value) {
        when(configService.getString(RegisterConfigKeys.REGISTER_DEFAULT_ROLE_CODE, ""))
            .thenReturn(value);
    }

    private SysTenant enabledTenant() {
        SysTenant tenant = new SysTenant();
        tenant.setId(TENANT_ID);
        tenant.setStatus(EntityStatus.ENABLED.getCode());
        return tenant;
    }

    private SysRole tenantRole() {
        SysRole role = new SysRole();
        role.setId(ROLE_ID);
        role.setTenantId(TENANT_ID);
        role.setCode("staff");
        role.setRoleType(AdminConstants.ROLE_TYPE_TENANT);
        role.setStatus(EntityStatus.ENABLED.getCode());
        return role;
    }

    @Test
    @DisplayName("开关关闭：直接拒绝，且不查询租户/角色、不落库")
    void shouldRejectWhenDisabled() {
        stubSwitch(false);

        assertThatThrownBy(() -> userService.registerUser(request()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("注册功能未开启");
        verifyNoInteractions(tenantMapper, roleMapper, accountSupport);
    }

    @Test
    @DisplayName("未配置默认租户：显式失败（不静默落到某个租户）")
    void shouldRejectWhenTenantConfigMissing() {
        stubSwitch(true);
        stubTenantConfig("");

        assertThatThrownBy(() -> userService.registerUser(request()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("注册未配置默认租户，请联系管理员");
        verifyNoInteractions(tenantMapper);
    }

    @Test
    @DisplayName("默认租户已停用：拒绝注册")
    void shouldRejectWhenTenantDisabled() {
        stubSwitch(true);
        stubTenantConfig(String.valueOf(TENANT_ID));
        SysTenant tenant = enabledTenant();
        tenant.setStatus(EntityStatus.DISABLED.getCode());
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(tenant);

        assertThatThrownBy(() -> userService.registerUser(request()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("注册默认租户不存在或已停用");
        verifyNoInteractions(roleMapper);
    }

    @Test
    @DisplayName("未配置默认角色：拒绝注册，不创建无角色用户")
    void shouldRejectWhenRoleConfigMissing() {
        stubSwitch(true);
        stubTenantConfig(String.valueOf(TENANT_ID));
        stubRoleConfig("");
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(enabledTenant());

        assertThatThrownBy(() -> userService.registerUser(request()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("注册未配置默认角色，请联系管理员");
        verifyNoInteractions(accountSupport);
    }

    @Test
    @DisplayName("默认角色不属于默认租户：拒绝注册")
    void shouldRejectWhenRoleBelongsToAnotherTenant() {
        stubSwitch(true);
        stubTenantConfig(String.valueOf(TENANT_ID));
        stubRoleConfig("staff");
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(enabledTenant());
        SysRole foreignRole = tenantRole();
        foreignRole.setTenantId(2L);
        when(roleMapper.selectOne(any(), eq(false))).thenReturn(foreignRole);

        assertThatThrownBy(() -> userService.registerUser(request()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("注册默认角色不存在、已停用或不属于默认租户");
    }

    @Test
    @DisplayName("默认角色没有任何菜单授权：拒绝注册（否则登录后是空系统）")
    void shouldRejectWhenRoleHasNoMenuGrant() {
        stubSwitch(true);
        stubTenantConfig(String.valueOf(TENANT_ID));
        stubRoleConfig("staff");
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(enabledTenant());
        when(roleMapper.selectOne(any(), eq(false))).thenReturn(tenantRole());
        when(roleMenuMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> userService.registerUser(request()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("注册默认角色未分配菜单权限");
        verifyNoInteractions(accountSupport);
    }

    @Test
    @DisplayName("注册成功：落默认租户 + 启用 + 租户用户 + 默认角色 + 密码历史")
    void shouldCreateUserWithDefaultTenantAndRole() {
        stubSwitch(true);
        stubTenantConfig(String.valueOf(TENANT_ID));
        stubRoleConfig("staff");
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(enabledTenant());
        when(roleMapper.selectOne(any(), eq(false))).thenAnswer(invocation -> {
            // 角色查询必须发生在显式租户作用域内，否则生产上会被租户插件 fail-closed 拒绝
            assertThat(TenantContext.getTenantId()).contains(TENANT_ID);
            return tenantRole();
        });
        when(roleMenuMapper.selectCount(any())).thenReturn(1L);
        UserRegisterReq req = request();

        SysUser created;
        try (MockedStatic<SysCache> ignoredCache = mockStatic(SysCache.class)) {
            doAnswer(invocation -> {
                SysUser saved = invocation.getArgument(0);
                saved.setId(USER_ID);
                return true;
            }).when(userService).save(any());
            created = userService.registerUser(req);
        }

        assertThat(created.getId()).isEqualTo(USER_ID);
        assertThat(created.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(created.getUserType()).isEqualTo(AdminConstants.USER_TYPE_TENANT);
        assertThat(created.getStatus()).isEqualTo(EntityStatus.ENABLED.getCode());
        // 未填昵称时取真实姓名，避免列表页空白
        assertThat(created.getNickname()).isEqualTo("爱丽丝");
        // 密码必须加密落库，且不以明文保存
        assertThat(created.getPassword()).startsWith("$2");
        assertThat(created.getPassword()).isNotEqualTo(req.getPassword());
        verify(accountSupport).checkUsernameUnique("alice", null);
        verify(accountSupport).checkPhoneUnique(isNull(), isNull());
        verify(accountSupport).validatePassword(req.getPassword(), "alice");
        verify(accountSupport).recordPasswordHistory(USER_ID, created.getPassword());
        verify(userRoleMapper).insertBatch(argThat(rows -> {
            List<SysUserRole> relations = List.copyOf(rows);
            return relations.size() == 1
                && USER_ID.equals(relations.get(0).getUserId())
                && ROLE_ID.equals(relations.get(0).getRoleId());
        }));
    }
}
