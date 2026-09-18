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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysPostMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserSocialMapper;
import cn.ypbin.admin.system.provider.AdminDataScopeHandler;
import cn.ypbin.admin.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.online.OnlineUserService;
import cn.ypbin.starter.tenant.autoconfigure.TenantProperties;
import cn.ypbin.starter.tenant.core.TenantContext;
import cn.ypbin.starter.tenant.handler.DefaultTenantLineHandler;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code SysUserServiceImpl#getByIdGlobal} 的租户忽略机制测试（缺陷 1 的机制级证明）。
 *
 * <p><b>背景</b>：{@code /auth/social/callback/**} 匿名可达，匿名链路没有网关签发的 {@code X-Tenant-Id}；
 * 而 {@code sys_user} <b>不在</b> {@code deploy/nacos/ypbin-system.yaml} 的 {@code ignore-tables} 里，
 * {@code ypbin.tenant.fail-on-missing-tenant=true} 又是 fail-closed。于是继承自 {@code IService} 的
 * {@code getById}（内部端点原先的写法）在第三方登录时必然抛「缺少租户上下文」。</p>
 *
 * <p><b>为什么用真实处理器而不是断言注解存在</b>：本测试直接实例化 starter 的
 * {@link DefaultTenantLineHandler}，并用与生产同样的配置（{@code sys_user} 不在忽略表 + fail-closed），
 * 断言的就是租户拦截器真正会看的两个判据——{@link DefaultTenantLineHandler#ignoreTable(String)}
 * 与 {@link DefaultTenantLineHandler#getTenantId()}。因此这是「拦截器会不会拦」的等价条件，
 * 而不是对代码形状的间接推断。三个用例互为反向证明：无上下文时 {@code getById} 必被拦、
 * {@code getByIdGlobal} 必放行、且忽略状态退出后不泄漏。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
class SysUserServiceImplTenantIgnoreTest {

    private static final String SYS_USER_TABLE = "sys_user";

    private static final Long USER_ID = 7L;

    private SysUserServiceImpl userService;

    private DefaultTenantLineHandler tenantHandler;

    private SysUser user;

    @BeforeEach
    void setUp() {
        userService = spy(new SysUserServiceImpl(
            mock(SysUserRoleMapper.class),
            mock(SysUserPostMapper.class),
            mock(SysUserSocialMapper.class),
            mock(SysRoleMapper.class),
            mock(SysPostMapper.class),
            mock(OnlineUserService.class),
            mock(UserExcelComponent.class),
            mock(UserAccountSupport.class),
            mock(AdminDataScopeHandler.class)));
        // 与 deploy/nacos/ypbin-system.yaml 同口径：fail-closed，且 sys_user 不在 ignore-tables
        TenantProperties properties = new TenantProperties();
        properties.setFailOnMissingTenant(true);
        properties.setIgnoreTables(List.of("sys_menu", "sys_config", "sys_log"));
        // 匿名链路：没有网关签发的租户头，provider 取不到租户
        tenantHandler = new DefaultTenantLineHandler(Optional::empty, properties);
        user = new SysUser();
        user.setId(USER_ID);
    }

    @Test
    @DisplayName("反向前提：无租户上下文时 sys_user 不在忽略表 ⇒ 拦截器 fail-closed 抛「缺少租户上下文」")
    void tenantFilterIsFailClosedWithoutContext() {
        assertThat(tenantHandler.ignoreTable(SYS_USER_TABLE)).isFalse();
        assertThatThrownBy(tenantHandler::getTenantId)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("缺少租户上下文");
    }

    @Test
    @DisplayName("getByIdGlobal：查询执行时拦截器必须跳过 sys_user，退出后忽略状态不泄漏")
    void globalLookupMustBypassFailClosedTenantFilter() {
        AtomicBoolean ignoredDuringLookup = new AtomicBoolean(false);
        AtomicBoolean interceptorWouldSkip = new AtomicBoolean(false);
        doAnswer(invocation -> {
            ignoredDuringLookup.set(TenantContext.isIgnored());
            interceptorWouldSkip.set(tenantHandler.ignoreTable(SYS_USER_TABLE));
            return user;
        }).when(userService).getById(USER_ID);

        SysUser result = userService.getByIdGlobal(USER_ID);

        assertThat(result).isSameAs(user);
        assertThat(ignoredDuringLookup)
            .as("查询执行时租户忽略必须已打开，否则第三方登录会撞 fail-closed")
            .isTrue();
        assertThat(interceptorWouldSkip)
            .as("租户拦截器必须跳过 sys_user，否则仍会追加 tenant_id 条件或直接抛错")
            .isTrue();
        assertThat(TenantContext.isIgnored())
            .as("executeIgnore 退出后必须恢复，不能把忽略状态泄漏到后续查询（跨租户越权风险）")
            .isFalse();
    }

    @Test
    @DisplayName("反向证明：直接走继承的 getById（旧写法）不会打开忽略作用域，拦截器仍会拦")
    void plainGetByIdMustNotBypassTenantFilter() {
        AtomicBoolean ignoredDuringLookup = new AtomicBoolean(true);
        AtomicBoolean interceptorWouldSkip = new AtomicBoolean(true);
        doAnswer(invocation -> {
            ignoredDuringLookup.set(TenantContext.isIgnored());
            interceptorWouldSkip.set(tenantHandler.ignoreTable(SYS_USER_TABLE));
            return user;
        }).when(userService).getById(USER_ID);

        userService.getById(USER_ID);

        assertThat(ignoredDuringLookup)
            .as("getById 若也打开忽略作用域，则上面那条「必须打开」的断言就不是在证明 getByIdGlobal 的作用")
            .isFalse();
        assertThat(interceptorWouldSkip)
            .as("getById 路径上拦截器必须仍受治理，否则本测试的前提（旧写法会被拦）不成立")
            .isFalse();
    }

    @Test
    @DisplayName("反向证明：getByIdGlobal 内部查询抛错时不吞异常，且忽略状态仍被恢复")
    void shouldNotSwallowFailureAndShouldRestoreContext() {
        doAnswer(invocation -> {
            throw new IllegalStateException("db down");
        }).when(userService).getById(USER_ID);

        assertThatThrownBy(() -> userService.getByIdGlobal(USER_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("db down");
        assertThat(TenantContext.isIgnored())
            .as("异常路径也必须退出忽略作用域（executeIgnore 的 finally 保证）")
            .isFalse();
    }
}
