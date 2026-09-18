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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.auth.support.AuthConfigReader;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.captcha.core.CaptchaService;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.core.TrackingEventCodes;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 登出埋点（{@code auth.user.logout}）单元测试。
 *
 * <p>登出的关键时序：userId/tenantId 只存在于 sa-token 会话，必须在 {@code LoginHelper.logout()}
 * <b>之前</b>取到——否则事件里的用户维度恒为空，按人做的漏斗/留存全部失真。本测试同时钉死
 * 「会话已销毁也能拿到用户维度」与「登出不受埋点影响」。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class AuthServiceLogoutTest {

    private AuthService buildService(LoginEventTracker tracker) {
        return new AuthService(mock(ISystemClient.class), mock(LoginSupport.class),
            mock(AuthConfigReader.class), mock(CaptchaService.class),
            mock(PasswordAttemptLimiter.class), tracker);
    }

    private LoginEventTracker trackerWith(TrackRecorder recorder) {
        return new LoginEventTracker(providerOf(recorder));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<TrackRecorder> providerOf(TrackRecorder recorder) {
        ObjectProvider<TrackRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recorder);
        return provider;
    }

    @Test
    void logoutShouldReportLogoutEventWithUserCapturedBeforeSessionDestroyed() {
        TrackRecorder recorder = mock(TrackRecorder.class);
        AuthService authService = buildService(trackerWith(recorder));

        LoginUser loginUser = new LoginUser(42L, "alice");
        loginUser.setTenantId(7L);
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class);
            MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class)) {
            userContext.when(UserContext::getLoginUser).thenReturn(Optional.of(loginUser));

            authService.logout("10.0.0.8", "junit-agent");

            // 会话确实被销毁（业务语义不变）
            loginHelper.verify(LoginHelper::logout);
            // 且事件带着销毁前取到的用户维度
            ArgumentCaptor<TrackEvent> captor = ArgumentCaptor.forClass(TrackEvent.class);
            verify(recorder).record(captor.capture());
            TrackEvent event = captor.getValue();
            assertThat(event.eventCode()).isEqualTo(TrackingEventCodes.AUTH_USER_LOGOUT);
            assertThat(event.context().userId()).isEqualTo(42L);
            assertThat(event.context().tenantId()).isEqualTo(7L);
            assertThat(event.context().clientIp()).isEqualTo("10.0.0.8");
            assertThat(event.context().userAgent()).isEqualTo("junit-agent");
            assertThat(event.payload()).isEmpty();
        }
    }

    /**
     * 埋点链路故障不得让登出失败：{@code LoginHelper.logout()} 必须已执行、方法不得抛出。
     */
    @Test
    void logoutShouldNotFailWhenTrackingBreaks() {
        TrackRecorder recorder = mock(TrackRecorder.class);
        doThrow(new IllegalStateException("埋点队列不可用")).when(recorder).record(any(TrackEvent.class));
        AuthService authService = buildService(trackerWith(recorder));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class);
            MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class)) {
            userContext.when(UserContext::getLoginUser).thenReturn(Optional.empty());

            authService.logout("10.0.0.8", null);

            loginHelper.verify(LoginHelper::logout);
        }
    }

    @Test
    void logoutWithoutSessionShouldStillReportEventWithoutUserDimension() {
        TrackRecorder recorder = mock(TrackRecorder.class);
        AuthService authService = buildService(trackerWith(recorder));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class);
            MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class)) {
            userContext.when(UserContext::getLoginUser).thenReturn(Optional.empty());

            authService.logout("10.0.0.8", null);

            ArgumentCaptor<TrackEvent> captor = ArgumentCaptor.forClass(TrackEvent.class);
            verify(recorder).record(captor.capture());
            assertThat(captor.getValue().eventCode()).isEqualTo(TrackingEventCodes.AUTH_USER_LOGOUT);
            // 无会话时用户维度留空（不臆造），事件仍上报
            assertThat(captor.getValue().context().userId()).isNull();
            loginHelper.verify(LoginHelper::logout);
        }
    }
}
