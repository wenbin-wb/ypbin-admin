/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysLog;
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.admin.system.provider.DbLogProviders;
import cn.ypbin.admin.system.service.SocialBindService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.social.SocialConfigReader;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 内部日志上报端点测试：{@code POST /internal/log-ingest} 必须复用既有的
 * {@code DbLogProviders.DbLogDao} 落库映射，不得在端点里再写一份字段映射。
 *
 * <p>做法是让端点接上真实的 {@code DbLogDao}（只 mock 最底层的 {@code SysLogMapper}），
 * 逐字段核对 {@code LogRecord → sys_log} 的结果——这样任何"端点自己映射了一遍"的改动都会在这里露馅。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class SystemClientImplLogIngestTest {

    private final SysLogMapper logMapper = mock(SysLogMapper.class);

    /** 埋点门面：{@code ypbin.tracking.enabled=true} 时 starter 才装配它 */
    private final TrackRecorder trackRecorder = mock(TrackRecorder.class);

    private SystemClientImpl controller() {
        return controllerWith(providerOf(trackRecorder));
    }

    private SystemClientImpl controllerWith(ObjectProvider<TrackRecorder> recorderProvider) {
        return new SystemClientImpl(
            mock(SysPermissionService.class),
            mock(SysUserService.class),
            mock(SysConfigMapper.class),
            mock(SocialConfigReader.class),
            mock(SocialBindService.class),
            mock(SysMenuService.class),
            new DbLogProviders.DbLogDao(logMapper),
            recorderProvider);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<TrackRecorder> providerOf(TrackRecorder recorder) {
        ObjectProvider<TrackRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recorder);
        return provider;
    }

    private TrackEvent loginEvent() {
        return new TrackEvent("evt-1", "auth.user.login", Instant.parse("2026-09-16T02:30:00Z"),
            null, null, null, null, null, null, true, Map.of("authType", "ACCOUNT"));
    }

    private LogRecord fullRecord() {
        LogRecord record = new LogRecord();
        record.setDescription("账号密码登录");
        record.setModule("认证");
        record.setRequestMethod("POST");
        record.setRequestUri("/login");
        record.setRequestParam("client=web");
        record.setRequestBody("{\"username\":\"alice\"}");
        record.setResponseBody("{\"code\":200}");
        record.setStatusCode(200);
        record.setIp("10.0.0.8");
        record.setLocation("广东省深圳市");
        record.setBrowser("Chrome 120");
        record.setOs("Windows 10");
        record.setClientId("web-admin");
        record.setClientType("WEB");
        record.setAuthType("ACCOUNT");
        record.setUserId(42L);
        record.setTimestamp(Instant.parse("2026-09-16T02:30:00Z"));
        record.setTimeTakenMillis(37L);
        record.setSuccess(true);
        return record;
    }

    @Test
    void ingestShouldLandEveryFieldThroughExistingDbLogDaoMapping() {
        R<Void> result = controller().ingestLog(fullRecord());

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<SysLog> captor = ArgumentCaptor.forClass(SysLog.class);
        verify(logMapper).insert(captor.capture());
        SysLog entity = captor.getValue();

        assertThat(entity.getDescription()).isEqualTo("账号密码登录");
        assertThat(entity.getModule()).isEqualTo("认证");
        assertThat(entity.getRequestMethod()).isEqualTo("POST");
        assertThat(entity.getRequestUri()).isEqualTo("/login");
        assertThat(entity.getRequestParam()).isEqualTo("client=web");
        assertThat(entity.getRequestBody()).isEqualTo("{\"username\":\"alice\"}");
        assertThat(entity.getResponseBody()).isEqualTo("{\"code\":200}");
        assertThat(entity.getStatusCode()).isEqualTo(200);
        assertThat(entity.getIp()).isEqualTo("10.0.0.8");
        assertThat(entity.getLocation()).isEqualTo("广东省深圳市");
        assertThat(entity.getBrowser()).isEqualTo("Chrome 120");
        assertThat(entity.getOs()).isEqualTo("Windows 10");
        assertThat(entity.getClientId()).isEqualTo("web-admin");
        assertThat(entity.getClientType()).isEqualTo("WEB");
        assertThat(entity.getAuthType()).isEqualTo("ACCOUNT");
        assertThat(entity.getOperateUserId()).isEqualTo(42L);
        assertThat(entity.getTimeTaken()).isEqualTo(37L);
        assertThat(entity.getSuccess()).isEqualTo(1);
        assertThat(entity.getOperateTime()).isEqualTo(
            LocalDateTime.ofInstant(Instant.parse("2026-09-16T02:30:00Z"), ZoneId.systemDefault()));
    }

    /**
     * 落库失败必须从端点抛出去（由全局处理器转成 HTTP 200 + {@code R.code}），
     * 端点上绝不能吞掉——否则调用方无法区分"上报成功"和"静默丢了"。
     */
    @Test
    void ingestShouldSurfacePersistenceFailureToCaller() {
        when(logMapper.insert(any(SysLog.class)))
            .thenThrow(new RuntimeException("sys_log 写入失败"));

        assertThatThrownBy(() -> controller().ingestLog(fullRecord()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("sys_log 写入失败");
    }

    /**
     * 埋点上报端点必须把事件交给 starter 的唯一写入口 {@code TrackRecorder}
     * （事件码登记校验 / 有界队列 / 消费者线程 / 落库全在那一侧），端点自己不做映射。
     */
    @Test
    void trackIngestShouldDelegateToTrackRecorder() {
        List<TrackEvent> events = List.of(loginEvent());

        R<Void> result = controller().ingestTrackEvents(events);

        assertThat(result.isSuccess()).isTrue();
        verify(trackRecorder).record(events);
    }

    /**
     * 埋点未启用（Bean 不存在）时必须显式失败，不得静默返回成功——
     * 否则上报方会以为「登录事件已落库」，实际什么都没发生。
     */
    @Test
    void trackIngestShouldFailExplicitlyWhenTrackingDisabled() {
        SystemClientImpl client = controllerWith(providerOf(null));

        assertThatThrownBy(() -> client.ingestTrackEvents(List.of(loginEvent())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("ypbin.tracking.enabled");
    }
}
