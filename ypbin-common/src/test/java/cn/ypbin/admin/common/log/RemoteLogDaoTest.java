/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.cloud.exception.FeignRemoteException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.event.LogEvent;
import cn.ypbin.starter.log.event.LogEventListener;
import cn.ypbin.starter.log.model.LogRecord;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * 跨服务日志上报测试。
 *
 * <p>钉住三件事：① 上报的就是采集模型本身（不做二次字段映射）；② system 侧返回失败 {@code R}
 * 时必须<b>上抛</b>而不是静默 return（失败无痕迹＝最坏的一种降级）；③ 失败由本类<b>带完整堆栈</b>
 * 记录——不能依赖 starter 的监听器（本仓固定的 {@code ypbin-starter-log:3.3.0} 只打 message），
 * 且异常到达监听器后被捕获，不逃逸到业务线程。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class RemoteLogDaoTest {

    private ListAppender<ILoggingEvent> appender;

    private Logger daoLogger;

    @BeforeEach
    void attachAppender() {
        daoLogger = (Logger) LoggerFactory.getLogger(RemoteLogDao.class);
        appender = new ListAppender<>();
        appender.start();
        daoLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        daoLogger.detachAppender(appender);
    }

    private LogRecord fullRecord() {
        LogRecord record = new LogRecord();
        record.setDescription("账号密码登录");
        record.setModule("认证");
        record.setRequestMethod("POST");
        record.setRequestUri("/login");
        record.setIp("10.0.0.8");
        record.setStatusCode(200);
        record.setUserId(42L);
        record.setTimestamp(Instant.parse("2026-09-16T02:30:00Z"));
        record.setTimeTakenMillis(37L);
        record.setSuccess(true);
        return record;
    }

    @Test
    void shouldReportCollectedRecordVerbatimToSystemService() {
        ISystemClient systemClient = mock(ISystemClient.class);
        when(systemClient.ingestLog(any())).thenReturn(R.ok());
        LogRecord record = fullRecord();

        new RemoteLogDao(systemClient).add(record);

        ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
        verify(systemClient).ingestLog(captor.capture());
        // 同一个对象过网：字段映射只在 system 侧的 DbLogDao 里发生一次
        assertThat(captor.getValue()).isSameAs(record);
        assertThat(captor.getValue().getModule()).isEqualTo("认证");
    }

    /**
     * system 不可达时 {@code ISystemClientFallback} 返回失败 {@code R}（HTTP 仍为 200），
     * 这种情况没有异常对象——必须显式抛，否则"日志没落库"完全无迹可寻。
     */
    @Test
    void shouldThrowInsteadOfSilentlyDroppingWhenRemoteReportsFailure() {
        ISystemClient systemClient = mock(ISystemClient.class);
        when(systemClient.ingestLog(any())).thenReturn(
            R.fail(GlobalErrorCode.UNAUTHORIZED.getCode(), "内部调用凭证校验失败"));

        assertThatThrownBy(() -> new RemoteLogDao(systemClient).add(fullRecord()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(String.valueOf(GlobalErrorCode.UNAUTHORIZED.getCode()))
            .hasMessageContaining("内部调用凭证校验失败");
        // 定位信息（模块/描述/URI）落在日志行里，且带完整堆栈
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getFormattedMessage())
                .contains("认证")
                .contains("账号密码登录")
                .contains("/login");
        });
    }

    @Test
    void shouldThrowWhenRemoteReturnsNullResult() {
        ISystemClient systemClient = mock(ISystemClient.class);
        when(systemClient.ingestLog(any())).thenReturn(null);

        assertThatThrownBy(() -> new RemoteLogDao(systemClient).add(fullRecord()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("响应为空");
    }

    /**
     * 失败必须由本类记 ERROR <b>且带 Throwable</b>（{@code ILoggingEvent.throwableProxy} 非空即
     * "打了完整堆栈"）——本仓固定的 starter 3.3.0 监听器只打 message，靠它满足不了"完整堆栈"这条铁律。
     * 同时异常被监听器捕获，不会反噬业务线程。
     */
    @Test
    void failureShouldBeLoggedWithFullStackTraceAndAbsorbedByListener() {
        ISystemClient systemClient = mock(ISystemClient.class);
        when(systemClient.ingestLog(any())).thenReturn(R.fail("系统服务暂不可用，请稍后重试"));
        LogEventListener listener = new LogEventListener(new RemoteLogDao(systemClient));

        assertThatCode(() -> listener.onLogEvent(new LogEvent(fullRecord())))
            .doesNotThrowAnyException();

        assertThat(appender.list)
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getMessage())
                    .contains("上报 system 服务失败");
            });
    }

    /**
     * 传输层异常（超时/熔断）同样要留下完整堆栈，并保留原始异常类型上抛。
     */
    @Test
    void transportFailureShouldKeepOriginalExceptionAndStack() {
        ISystemClient systemClient = mock(ISystemClient.class);
        when(systemClient.ingestLog(any())).thenThrow(new FeignRemoteException(500, "网关超时", 504, "ingestLog"));

        assertThatThrownBy(() -> new RemoteLogDao(systemClient).add(fullRecord()))
            .isInstanceOf(FeignRemoteException.class);

        assertThat(appender.list)
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
            });
    }
}
