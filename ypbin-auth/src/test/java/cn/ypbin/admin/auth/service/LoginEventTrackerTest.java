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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.core.TrackingEventCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link LoginEventTracker} 单元测试。
 *
 * <p>本仓长期没有任何登录埋点的发送方（漏斗里缺「登录」这一步），因此这里要钉死四件事：
 * 事件码正确、payload 只含白名单属性且不带任何敏感值、请求维度（IP/UA/链路 ID/用户/租户）
 * 在请求线程上被捕获、以及<b>上报失败绝不反噬业务</b>。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class LoginEventTrackerTest {

    /** 与 starter 采集侧一致：网关链路 ID 头 */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final String CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/120.0.0.0 Safari/537.36";

    private ListAppender<ILoggingEvent> appender;

    private Logger trackerLogger;

    @BeforeEach
    void attachAppender() {
        trackerLogger = (Logger) LoggerFactory.getLogger(LoginEventTracker.class);
        appender = new ListAppender<>();
        appender.start();
        trackerLogger.addAppender(appender);
        // 模拟请求线程：链路 ID 只能从请求头取（消费者线程取不到）
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(REQUEST_ID_HEADER, "trace-abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void detachAppender() {
        trackerLogger.detachAppender(appender);
        RequestContextHolder.resetRequestAttributes();
    }

    private SysUser buildUser() {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setUsername("alice");
        user.setTenantId(7L);
        return user;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<TrackRecorder> providerOf(TrackRecorder recorder) {
        ObjectProvider<TrackRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recorder);
        return provider;
    }

    @Test
    void loginShouldReportAuthTypePayloadAndRequestDimensions() {
        TrackRecorder recorder = mock(TrackRecorder.class);
        LoginEventTracker tracker = new LoginEventTracker(providerOf(recorder));

        tracker.recordLogin(buildUser(), "ACCOUNT", "10.0.0.8", CHROME_UA);

        ArgumentCaptor<TrackEvent> captor = ArgumentCaptor.forClass(TrackEvent.class);
        verify(recorder).record(captor.capture());
        TrackEvent event = captor.getValue();
        assertThat(event.eventCode()).isEqualTo(TrackingEventCodes.AUTH_USER_LOGIN);
        assertThat(event.success()).isTrue();
        // payload 只放目录白名单里的 authType，且不含任何凭据/联系方式
        assertThat(event.payload()).containsOnlyKeys("authType");
        assertThat(event.payload()).containsEntry("authType", "ACCOUNT");
        assertThat(event.payload().toString()).doesNotContain("password", "token", "phone", "alice");
        // 请求线程上捕获的维度：消费者线程再也补不到，必须在这一刻带上
        assertThat(event.context()).isNotNull();
        assertThat(event.context().userId()).isEqualTo(42L);
        assertThat(event.context().tenantId()).isEqualTo(7L);
        assertThat(event.context().clientIp()).isEqualTo("10.0.0.8");
        assertThat(event.context().userAgent()).isEqualTo(CHROME_UA);
        assertThat(event.context().traceId()).isEqualTo("trace-abc");
    }

    @Test
    void logoutShouldReportEmptyPayloadAndKeepUserDimension() {
        TrackRecorder recorder = mock(TrackRecorder.class);
        LoginEventTracker tracker = new LoginEventTracker(providerOf(recorder));

        tracker.recordLogout(42L, 7L, "10.0.0.8", null);

        ArgumentCaptor<TrackEvent> captor = ArgumentCaptor.forClass(TrackEvent.class);
        verify(recorder).record(captor.capture());
        TrackEvent event = captor.getValue();
        assertThat(event.eventCode()).isEqualTo(TrackingEventCodes.AUTH_USER_LOGOUT);
        // 目录里 auth.user.logout 没有声明任何属性，payload 必须为空表（不能塞"顺手拿到"的用户名等）
        assertThat(event.payload()).isEmpty();
        assertThat(event.context().userId()).isEqualTo(42L);
        assertThat(event.context().userAgent()).isNull();
    }

    /**
     * 埋点链路故障（含 starter 事件构造期校验失败）不得抛出到登录/登出主流程，但必须留完整堆栈。
     */
    @Test
    void recorderFailureShouldBeLoggedWithStackAndNotPropagate() {
        TrackRecorder recorder = mock(TrackRecorder.class);
        doThrow(new IllegalStateException("队列写入失败")).when(recorder).record(any(TrackEvent.class));
        LoginEventTracker tracker = new LoginEventTracker(providerOf(recorder));

        tracker.recordLogin(buildUser(), "PHONE", "10.0.0.8", CHROME_UA);
        tracker.recordLogout(42L, 7L, "10.0.0.8", CHROME_UA);

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
        });
    }

    /**
     * 埋点未启用（{@code ypbin.tracking.enabled} 未开）：不采集是配置语义，但必须留一行 WARN 指明原因，
     * 且只提示一次（不能每次登录都刷屏）。
     */
    @Test
    void missingRecorderShouldWarnOnceAndNotFail() {
        LoginEventTracker tracker = new LoginEventTracker(providerOf(null));

        tracker.recordLogin(buildUser(), "SOCIAL", "10.0.0.8", CHROME_UA);
        tracker.recordLogout(42L, 7L, "10.0.0.8", CHROME_UA);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("ypbin.tracking.enabled");
    }

    @Test
    void blankIpAndUserAgentShouldBeNormalizedToNull() {
        TrackRecorder recorder = mock(TrackRecorder.class);
        LoginEventTracker tracker = new LoginEventTracker(providerOf(recorder));

        tracker.recordLogin(buildUser(), "ACCOUNT", "  ", "");

        ArgumentCaptor<TrackEvent> captor = ArgumentCaptor.forClass(TrackEvent.class);
        verify(recorder, times(1)).record(captor.capture());
        // 空串不写进维度（与 starter 的 TrackRequestContextResolver 归一策略一致）
        assertThat(captor.getValue().context().clientIp()).isNull();
        assertThat(captor.getValue().context().userAgent()).isNull();
    }
}
