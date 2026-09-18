/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysLog;
import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.starter.log.event.LogEvent;
import cn.ypbin.starter.log.event.LogEventListener;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 操作日志落库数据源测试。
 *
 * <p>用 Mockito 捕获 {@code SysLogMapper.insert} 的入参，逐字段核对
 * {@code LogRecord} → {@code sys_log} 的映射（尤其 success 布尔→0/1、时间 Instant→LocalDateTime），
 * 并核对操作人数据源取网关身份头。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class DbLogProvidersTest {

    @AfterEach
    void clearIdentity() {
        IdentityContext.clear();
    }

    private LogRecord fullRecord() {
        LogRecord record = new LogRecord();
        record.setDescription("新增用户");
        record.setModule("用户管理");
        record.setRequestMethod("POST");
        record.setRequestUri("/user");
        record.setRequestParam("id=1");
        record.setRequestBody("{\"id\":1}");
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
    void shouldMapLogRecordToSysLogFields() {
        SysLogMapper mapper = mock(SysLogMapper.class);
        new DbLogProviders.DbLogDao(mapper).add(fullRecord());

        ArgumentCaptor<SysLog> captor = ArgumentCaptor.forClass(SysLog.class);
        verify(mapper).insert(captor.capture());
        SysLog entity = captor.getValue();

        assertThat(entity.getDescription()).isEqualTo("新增用户");
        assertThat(entity.getModule()).isEqualTo("用户管理");
        assertThat(entity.getRequestMethod()).isEqualTo("POST");
        assertThat(entity.getRequestUri()).isEqualTo("/user");
        assertThat(entity.getRequestParam()).isEqualTo("id=1");
        assertThat(entity.getRequestBody()).isEqualTo("{\"id\":1}");
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
        // Instant → LocalDateTime 按 JVM 时区换算
        assertThat(entity.getOperateTime()).isEqualTo(
            LocalDateTime.ofInstant(Instant.parse("2026-09-16T02:30:00Z"), ZoneId.systemDefault()));
        // 主键由 IdType.ASSIGN_ID 生成，落库前不得自行赋值
        assertThat(entity.getId()).isNull();
    }

    @Test
    void shouldMapFailureToZeroFlagAndKeepErrorMsg() {
        SysLogMapper mapper = mock(SysLogMapper.class);
        LogRecord record = fullRecord();
        record.setSuccess(false);
        record.setErrorMsg("用户已存在");

        new DbLogProviders.DbLogDao(mapper).add(record);

        ArgumentCaptor<SysLog> captor = ArgumentCaptor.forClass(SysLog.class);
        verify(mapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getSuccess()).isZero();
        assertThat(captor.getValue().getErrorMsg()).isEqualTo("用户已存在");
    }

    @Test
    void shouldTolerateNullOptionalFields() {
        SysLogMapper mapper = mock(SysLogMapper.class);
        LogRecord record = new LogRecord();
        record.setTimestamp(Instant.parse("2026-09-16T02:30:00Z"));
        record.setSuccess(true);

        new DbLogProviders.DbLogDao(mapper).add(record);

        ArgumentCaptor<SysLog> captor = ArgumentCaptor.forClass(SysLog.class);
        verify(mapper).insert(captor.capture());
        SysLog entity = captor.getValue();
        // 未采集项一律留空，不得填造默认文案掩盖"没采集到"
        assertThat(entity.getIp()).isNull();
        assertThat(entity.getBrowser()).isNull();
        assertThat(entity.getOperateUserId()).isNull();
        assertThat(entity.getSuccess()).isEqualTo(1);
    }

    @Test
    void shouldReadOperatorFromIdentityContext() {
        LoginUser loginUser = new LoginUser(42L, "alice");
        IdentityContext.setLoginUser(loginUser);

        assertThat(new DbLogProviders.IdentityLogUserProvider().getCurrentUserId()).contains(42L);
    }

    @Test
    void shouldReturnEmptyOperatorWhenNoIdentityHeader() {
        // 匿名/系统操作（定时任务、开放接口）没有身份头，必须返回空而不是抛错
        assertThat(new DbLogProviders.IdentityLogUserProvider().getCurrentUserId()).isEmpty();
    }

    /**
     * 落库失败必须上抛，绝不能被 {@code DbLogDao} 吞掉——否则 {@code sys_log} 缺记录且毫无线索。
     * 可见日志由 starter 的 {@code LogEventListener.onLogEvent} 在捕获后 WARN 打印
     * （3.3.0 {@code LogEventListener.java:46-50}），此处只断言本类不吞异常。
     */
    @Test
    void shouldPropagateInsertFailureInsteadOfSwallowingIt() {
        SysLogMapper mapper = mock(SysLogMapper.class);
        when(mapper.insert(any(SysLog.class)))
            .thenThrow(new RuntimeException("sys_log 写入失败"));

        assertThatThrownBy(() -> new DbLogProviders.DbLogDao(mapper).add(fullRecord()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("sys_log 写入失败");
    }

    /**
     * 与 starter 的异步监听器串起来看行为：落库抛错时监听器捕获并记日志，
     * 不让异常逃逸到业务线程（业务已成功，日志失败不得反噬）。
     */
    @Test
    void listenerShouldContainInsertFailureAwayFromBusinessThread() {
        SysLogMapper mapper = mock(SysLogMapper.class);
        when(mapper.insert(any(SysLog.class)))
            .thenThrow(new RuntimeException("sys_log 写入失败"));
        LogEventListener listener = new LogEventListener(new DbLogProviders.DbLogDao(mapper));

        assertThatCode(() -> listener.onLogEvent(new LogEvent(fullRecord()))).doesNotThrowAnyException();
    }
}
