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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
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
import cn.ypbin.starter.security.online.OnlineUserHelper;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@link LoginSupport} 单元测试。
 *
 * <p>验证登录收尾流程：建立会话 → 角色码缓存 → 写入 LoginUser → 记录登录终端信息 → 回写最后登录时间。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
class LoginSupportTest {

    /** 登录埋点上报器：本类只断言「被调用且不阻断」，事件构造细节由 LoginEventTrackerTest 覆盖 */
    private final LoginEventTracker loginEventTracker = mock(LoginEventTracker.class);

    /** 用于断言终端信息的 User-Agent（Chrome on Windows） */
    private static final String CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/120.0.0.0 Safari/537.36";

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
        ISystemClient systemClient = mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient, loginEventTracker);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class);
            MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of("admin", "user"));
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(mock(SaSession.class));
            stpUtil.when(() -> StpUtil.getTokenSession()).thenReturn(mock(SaSession.class));

            LoginResp resp = support.completeLogin(buildUser(), "ACCOUNT", "10.0.0.8", CHROME_UA);

            assertThat(resp.getAccessToken()).isEqualTo("mock-token");
            // 回写最后登录时间（Feign 调用）
            verify(systemClient).updateLastLoginTime(42L);
        }
    }

    /**
     * 在线用户列表的 IP/浏览器/操作系统只来自登录时写入 Token-Session 的终端信息，
     * 必须验证确实写入且 User-Agent 被解析成展示文案。
     */
    @Test
    void completeLoginShouldRecordTerminalInfoForOnlineUser() {
        ISystemClient systemClient = mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient, loginEventTracker);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of());
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(mock(SaSession.class));
            SaSession tokenSession = mock(SaSession.class);
            stpUtil.when(StpUtil::getTokenSession).thenReturn(tokenSession);

            support.completeLogin(buildUser(), "ACCOUNT", "10.0.0.8", CHROME_UA);

            ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            verify(tokenSession).set(eq(OnlineUserHelper.KEY_TERMINAL), valueCaptor.capture());
            OnlineUserHelper.Terminal terminal = (OnlineUserHelper.Terminal) valueCaptor.getValue();
            assertThat(terminal.getIp()).isEqualTo("10.0.0.8");
            assertThat(terminal.getBrowser()).isEqualTo("Chrome 120.0.0.0");
            // hutool 对 Windows NT 10.0 的命名随版本变化（"Windows 10 or Windows Server 2016"），
            // 只断言族名，避免把 hutool 的措辞写死在测试里
            assertThat(terminal.getOs()).contains("Windows");
            // 登录时间由 OnlineUserHelper 自动补
            assertThat(terminal.getLoginTime()).isNotNull();
            // 归属地未接入离线 IP 库，必须留空而不是臆造
            assertThat(terminal.getLocation()).isNull();
        }
    }

    @Test
    void shouldRecordTerminalWithoutUserAgent() {
        ISystemClient systemClient = mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient, loginEventTracker);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of());
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(mock(SaSession.class));
            SaSession tokenSession = mock(SaSession.class);
            stpUtil.when(StpUtil::getTokenSession).thenReturn(tokenSession);

            // UA 缺失时 IP 仍要记上，浏览器/操作系统留空（不填造）
            support.completeLogin(buildUser(), "ACCOUNT", "10.0.0.8", null);

            ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            verify(tokenSession).set(eq(OnlineUserHelper.KEY_TERMINAL), valueCaptor.capture());
            OnlineUserHelper.Terminal terminal = (OnlineUserHelper.Terminal) valueCaptor.getValue();
            assertThat(terminal.getIp()).isEqualTo("10.0.0.8");
            assertThat(terminal.getBrowser()).isNull();
            assertThat(terminal.getOs()).isNull();
        }
    }

    /**
     * 终端信息写入失败（如无 sa-token 上下文）不得阻断登录成功，但必须记录完整堆栈。
     */
    @Test
    void terminalRecordFailureShouldNotBreakLogin() {
        ISystemClient systemClient = mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient, loginEventTracker);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of());
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(mock(SaSession.class));
            stpUtil.when(StpUtil::getTokenSession).thenThrow(new IllegalStateException("无 sa-token 上下文"));

            LoginResp resp = support.completeLogin(buildUser(), "ACCOUNT", "10.0.0.8", CHROME_UA);

            assertThat(resp.getAccessToken()).isEqualTo("mock-token");
            verify(systemClient).updateLastLoginTime(42L);
        }
    }

    @Test
    void completeLoginShouldFallbackEmptyRolesWhenCacheFails() {
        ISystemClient systemClient = mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient, loginEventTracker);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            // 角色缓存读取失败（system-svc 不可用）：降级为空角色，登录不受阻
            sysCache.when(() -> SysCache.getUserRoleCodes(42L))
                .thenThrow(new RuntimeException("system-svc 不可用"));
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(mock(SaSession.class));
            stpUtil.when(() -> StpUtil.getTokenSession()).thenReturn(mock(SaSession.class));

            LoginResp resp = support.completeLogin(buildUser(), "ACCOUNT", "10.0.0.8", CHROME_UA);

            assertThat(resp.getAccessToken()).isEqualTo("mock-token");
            verify(systemClient).updateLastLoginTime(42L);
        }
    }

    @Test
    void updateLastLoginTimeFailureShouldNotBreakLogin() {
        ISystemClient systemClient = mock(ISystemClient.class);
        LoginSupport support = new LoginSupport(systemClient, loginEventTracker);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of());
            stpUtil.when(() -> StpUtil.getSession()).thenReturn(mock(SaSession.class));
            stpUtil.when(() -> StpUtil.getTokenSession()).thenReturn(mock(SaSession.class));
            // 回写失败不阻断登录
            doThrow(new RuntimeException("回写失败"))
                .when(systemClient).updateLastLoginTime(42L);

            LoginResp resp = support.completeLogin(buildUser(), "ACCOUNT", "10.0.0.8", CHROME_UA);

            assertThat(resp.getAccessToken()).isEqualTo("mock-token");
        }
    }

    /**
     * 三个登录入口（账号密码 / 短信 / 第三方）都经 {@link LoginSupport} 收尾，
     * 因此这里按三种 authType 各跑一次，钉死「三入口全覆盖」，并确认上报发生在登录成功之后。
     */
    @Test
    void completeLoginShouldReportLoginEventForEveryAuthType() {
        ISystemClient systemClient = mock(ISystemClient.class);
        LoginEventTracker tracker = mock(LoginEventTracker.class);
        LoginSupport support = new LoginSupport(systemClient, tracker);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of());
            stpUtil.when(StpUtil::getSession).thenReturn(mock(SaSession.class));
            stpUtil.when(StpUtil::getTokenSession).thenReturn(mock(SaSession.class));

            SysUser user = buildUser();
            for (String authType : List.of("ACCOUNT", "PHONE", "SOCIAL")) {
                support.completeLogin(user, authType, "10.0.0.8", CHROME_UA);
                verify(tracker).recordLogin(user, authType, "10.0.0.8", CHROME_UA);
            }
        }
    }

    /**
     * 埋点链路故障（本测试用真实 {@link LoginEventTracker} + 抛异常的 TrackRecorder 模拟）
     * 不得反噬登录：令牌照常返回，异常被包在埋点侧并记完整堆栈。
     */
    @Test
    @SuppressWarnings("unchecked")
    void loginEventReportingFailureShouldNotBreakLogin() {
        ISystemClient systemClient = mock(ISystemClient.class);
        TrackRecorder recorder = mock(TrackRecorder.class);
        doThrow(new IllegalStateException("埋点队列不可用")).when(recorder).record(any(TrackEvent.class));
        ObjectProvider<TrackRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recorder);
        LoginSupport support = new LoginSupport(systemClient, new LoginEventTracker(provider));

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue).thenReturn("mock-token");
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of());
            stpUtil.when(StpUtil::getSession).thenReturn(mock(SaSession.class));
            stpUtil.when(StpUtil::getTokenSession).thenReturn(mock(SaSession.class));

            LoginResp resp = support.completeLogin(buildUser(), "ACCOUNT", "10.0.0.8", CHROME_UA);

            assertThat(resp.getAccessToken()).isEqualTo("mock-token");
            verify(systemClient).updateLastLoginTime(42L);
        }
    }

    /**
     * 顺序断言：令牌先取、埋点后报。取令牌就失败时，绝不能先产出一条「登录成功」的埋点事件，
     * 否则事件口径与业务结果会分叉（复核意见：原顺序无测试保护，2026-09-16）。
     */
    @Test
    void loginEventMustBeReportedOnlyAfterTokenIsResolved() {
        ISystemClient systemClient = mock(ISystemClient.class);
        LoginEventTracker tracker = mock(LoginEventTracker.class);
        LoginSupport support = new LoginSupport(systemClient, tracker);

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class);
            MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class);
            MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {

            loginHelper.when(() -> LoginHelper.login(any(), any(), any())).thenAnswer(inv -> null);
            loginHelper.when(LoginHelper::getTokenValue)
                .thenThrow(new IllegalStateException("取令牌失败"));
            sysCache.when(() -> SysCache.getUserRoleCodes(42L)).thenReturn(List.of());
            stpUtil.when(StpUtil::getSession).thenReturn(mock(SaSession.class));
            stpUtil.when(StpUtil::getTokenSession).thenReturn(mock(SaSession.class));

            try {
                support.completeLogin(buildUser(), "ACCOUNT", "10.0.0.8", CHROME_UA);
            } catch (IllegalStateException expected) {
                // 预期：取令牌失败直接抛出
            }
            verify(tracker, never()).recordLogin(any(), any(), any(), any());
        }
    }
}
