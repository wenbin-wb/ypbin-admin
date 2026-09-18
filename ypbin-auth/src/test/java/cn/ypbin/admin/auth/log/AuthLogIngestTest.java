/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.admin.common.log.RemoteLogDao;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.log.aspect.LogAspect;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.event.LogEvent;
import cn.ypbin.starter.log.event.LogEventListener;
import cn.ypbin.starter.log.support.LogCollector;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * 登录日志上报失败不影响登录主流程（auth 侧）。
 *
 * <p>auth 没有数据源，其 {@code @Log} 采集到的记录经 {@link RemoteLogDao} 上报 system 落 {@code sys_log}。
 * 本测试刻意走<b>完整切面链路</b>（{@code LogAspect} → 事件 → {@code LogEventListener} → {@code RemoteLogDao}），
 * 并让 system 返回失败 {@code R}（不可达/凭证不匹配/落库失败都是这个形状），断言两件事：</p>
 * <ol>
 *   <li>被 {@code @Log} 包裹的登录方法<b>照常返回业务结果</b>（日志失败不得反噬登录）；</li>
 *   <li>失败被<b>带完整堆栈</b>记录（{@code throwableProxy} 非空），不是静默吞掉。</li>
 * </ol>
 *
 * <p>说明：这里事件发布是同步的（单测无 Spring 容器、{@code @Async} 不生效），
 * 也就是<b>比生产更坏</b>的情况——生产上监听器在异步线程执行；最坏情况都不影响业务，生产更不会。</p>
 *
 * <p>另注：堆栈断言挂在 {@code RemoteLogDao} 自己的 logger 上。本仓固定的
 * {@code ypbin-starter-log:3.3.0} 的 {@code LogEventListener} 只打 {@code e.getMessage()}、
 * <b>不带 Throwable</b>（补堆栈的 starter 提交 {@code 0dc6ef1} 晚于 v3.3.0），
 * 所以"完整堆栈"必须由上报侧自己保证。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class AuthLogIngestTest {

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

    @Test
    void failingLogIngestShouldNotAffectLoginResultButMustBeLogged() throws Throwable {
        ISystemClient systemClient = mock(ISystemClient.class);
        when(systemClient.ingestLog(any()))
            .thenReturn(R.fail("系统服务暂不可用，请稍后重试"));
        LogEventListener listener = new LogEventListener(new RemoteLogDao(systemClient));

        LogCollector collector = new LogCollector(Optional::empty, Optional::empty, ip -> null,
            new ObjectMapper());
        LogAspect aspect = new LogAspect(collector, event -> {
            if (event instanceof LogEvent logEvent) {
                listener.onLogEvent(logEvent);
            }
        }, Set.of(Include.IP, Include.REQUEST_PARAM));

        // 登录方法照常返回：日志上报失败不得改变业务结果
        assertThat(aspect.around(loginJoinPoint())).isEqualTo("login-ok");

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getMessage())
                .contains("上报 system 服务失败");
        });
    }

    private ProceedingJoinPoint loginJoinPoint() throws Throwable {
        Method login = FakeLoginApi.class.getMethod("login");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(login);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenReturn("login-ok");
        return point;
    }

    /**
     * 被 {@code @Log} 包裹的登录方法替身（与 {@code AuthController.login} 同形）。
     */
    static class FakeLoginApi {

        @Log(value = "账号密码登录", module = "认证")
        public String login() {
            return "login-ok";
        }
    }
}
