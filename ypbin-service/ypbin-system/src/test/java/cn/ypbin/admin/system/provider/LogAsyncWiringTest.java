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

import cn.ypbin.starter.async.autoconfigure.AsyncAnnotationAutoConfiguration;
import cn.ypbin.starter.async.autoconfigure.AsyncAutoConfiguration;
import cn.ypbin.starter.log.autoconfigure.LogAutoConfiguration;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.event.LogEvent;
import cn.ypbin.starter.log.event.LogEventListener;
import cn.ypbin.starter.log.model.LogRecord;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 异步落库装配测试：证明 {@code ypbin-starter-async} 到位后 starter 的
 * {@code LogEventListener}（标了 {@code @Async}）<b>真的</b>跑在异步线程上。
 *
 * <p>不引入该依赖时 {@code @EnableAsync} 不生效、{@code @Async} 被静默忽略，{@code sys_log} 的
 * insert 就落在业务请求线程里——这正是任务 2 要修的静默失效。本测试不依赖"注解存在"这种间接证据，
 * 而是实地量线程名：落库回调必须跑在 starter 的 {@code ypbin-async-*} 线程上，且不是调用线程。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class LogAsyncWiringTest {

    @Test
    void logPersistenceShouldRunOnStarterAsyncExecutorInsteadOfCallerThread() {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> persistThread = new AtomicReference<>();
        LogDao recordingDao = logRecord -> {
            persistThread.set(Thread.currentThread().getName());
            done.countDown();
        };

        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AsyncAutoConfiguration.class,
                AsyncAnnotationAutoConfiguration.class, LogAutoConfiguration.class))
            .withBean(LogDao.class, () -> recordingDao)
            .run(context -> {
                assertThat(context).hasNotFailed();
                LogEventListener listener = context.getBean(LogEventListener.class);

                String callerThread = Thread.currentThread().getName();
                listener.onLogEvent(new LogEvent(new LogRecord()));

                // 虚拟线程模式下任务在新线程执行，主线程立即返回：不阻塞即为"没占用业务线程"
                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(persistThread.get())
                    .isNotEqualTo(callerThread)
                    .startsWith("ypbin-async-");
            });
    }
}
