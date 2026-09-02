/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link LoginSupport} 单元测试。
 *
 * <p>验证登录收尾流程：建立会话 → 角色码缓存 → 写入 LoginUser → 回写最后登录时间。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
class LoginSupportTest {

    private SysUser buildUser() {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setUsername("alice");
        user.setRealName("爱丽丝");
        user.setTenantId(7L);
        user.setDeptId(3L);
        return user;
    }

    @Test
    void completeLoginShouldWriteSessionAndReturnToken() {
        ISystemClient systemClient = org.mockito.Mockito.mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class);
            MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of("admin", "user"));
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(org.mockito.Mockito.mock(
                cn.dev33.satoken.session.SaSession.class));

            LoginResp resp = support.completeLogin(buildUser(), "ACCOUNT");

            assertThat(resp.getAccessToken()).isEqualTo("mock-token");
            // 回写最后登录时间（Feign 调用）
            verify(systemClient).updateLastLoginTime(42L);
        }
    }

    @Test
    void completeLoginShouldFallbackEmptyRolesWhenCacheFails() {
        ISystemClient systemClient = org.mockito.Mockito.mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            // 角色缓存读取失败（system-svc 不可用）：降级为空角色，登录不受阻
            sysCache.when(() -> SysCache.getUserRoleCodes(42L))
                .thenThrow(new RuntimeException("system-svc 不可用"));
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(org.mockito.Mockito.mock(
                cn.dev33.satoken.session.SaSession.class));

            LoginResp resp = support.completeLogin(buildUser(), "ACCOUNT");

            assertThat(resp.getAccessToken()).isEqualTo("mock-token");
            verify(systemClient).updateLastLoginTime(42L);
        }
    }

    @Test
    void updateLastLoginTimeFailureShouldNotBreakLogin() {
        ISystemClient systemClient = org.mockito.Mockito.mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of());
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(org.mockito.Mockito.mock(
                cn.dev33.satoken.session.SaSession.class));
            // 回写失败不阻断登录
            org.mockito.Mockito.doThrow(new RuntimeException("回写失败"))
                .when(systemClient).updateLastLoginTime(42L);

            LoginResp resp = support.completeLogin(buildUser(), "ACCOUNT");

            assertThat(resp.getAccessToken()).isEqualTo("mock-token");
        }
    }
}
