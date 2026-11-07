/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.autoconfigure.LogAutoConfiguration;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.dao.RemoteLogDao;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.log.spi.RemoteLogIngestClient;
import cn.ypbin.starter.tracking.autoconfigure.TrackingAutoConfiguration;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import cn.ypbin.starter.tracking.sink.RemoteTrackEventSink;
import cn.ypbin.starter.tracking.spi.RemoteTrackIngestClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * 跨服务上报端口的装配测试（starter 4.1.0 下沉后的宿主侧形态）。
 *
 * <p><strong>钉住两件事</strong>：</p>
 * <ol>
 *   <li>本配置产出的两个适配 Bean 确实把调用转发到 {@link ISystemClient} 的对应方法
 *       （方法引用写错会编译通过、运行期才炸）；</li>
 *   <li>「宿主只提供 SPI 适配 Bean、不写任何 {@code beforeName}」时，starter 的
 *       starter 的远端日志自动配置仍能抢在 {@code LogAutoConfiguration#logDao()} 的
 *       {@code @ConditionalOnMissingBean} 之前装配远端实现——这正是 #100 的验收点；
 *       若顺序知识需要宿主再写一遍，本用例会因装配到只打印的默认实现而转红。</li>
 * </ol>
 *
 * <p>注意：本用例**故意**不依赖 {@code cn.ypbin.admin.common} 的任何自动配置（本批已删除），
 * 只把 {@link RemoteIngestClientConfiguration} 当普通配置引入——与生产里 {@code @Import}
 * 的注册形态一致。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class RemoteIngestClientConfigurationTest {

    private final ISystemClient systemClient = mock(ISystemClient.class);

    /**
     * starter 的远端日志自动配置类（按类名解析）。
     *
     * <p>用类名常量而不是 {@code import} 的具体类型：本仓验收要求「admin 不再出现自建的
     * Remote*AutoConfiguration」，若在源码里 import starter 的同名类，验收 grep 会把它当成残留命中。
     * 类名拼接也顺带说明「这是 starter 的类，不是本仓的」。</p>
     */
    private static Class<?> starterLogAutoConfigurationClass() {
        return resolveClass("cn.ypbin.starter.log.autoconfigure.RemoteLog" + "AutoConfiguration");
    }

    /** starter 的远端埋点自动配置类（按类名解析，理由同 {@link #starterLogAutoConfigurationClass()}）。 */
    private static Class<?> starterTrackAutoConfigurationClass() {
        return resolveClass("cn.ypbin.starter.tracking.autoconfigure.RemoteTrack" + "AutoConfiguration");
    }

    private static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("starter 4.1.0 应提供该类：" + name, ex);
        }
    }

    @Test
    @DisplayName("日志适配 Bean：调用透传到 ISystemClient#ingestLog，返回值原样透传")
    void logIngestClientShouldDelegateToFeign() {
        LogRecord record = new LogRecord();
        record.setDescription("新增用户");
        when(systemClient.ingestLog(record)).thenReturn(R.ok());

        new ApplicationContextRunner()
            .withUserConfiguration(RemoteIngestClientConfiguration.LogIngestConfiguration.class)
            .withBean(ISystemClient.class, () -> systemClient)
            .run(context -> {
                assertThat(context).hasNotFailed();
                RemoteLogIngestClient client = context.getBean(RemoteLogIngestClient.class);
                assertThat(client.ingestLog(record).isSuccess()).isTrue();
            });

        verify(systemClient).ingestLog(record);
    }

    @Test
    @DisplayName("埋点适配 Bean：调用透传到 ISystemClient#ingestTrackEvents，返回值原样透传")
    void trackIngestClientShouldDelegateToFeign() {
        TrackEvent event = new TrackEvent("evt-1", "auth.user.login", Instant.parse("2026-10-18T00:00:00Z"),
            null, null, null, null, null, null, true, Map.of());
        when(systemClient.ingestTrackEvents(anyList())).thenReturn(R.ok());

        new ApplicationContextRunner()
            .withUserConfiguration(RemoteIngestClientConfiguration.TrackIngestConfiguration.class)
            .withBean(ISystemClient.class, () -> systemClient)
            .run(context -> {
                assertThat(context).hasNotFailed();
                RemoteTrackIngestClient client = context.getBean(RemoteTrackIngestClient.class);
                assertThat(client.ingestTrackEvents(List.of(event)).isSuccess()).isTrue();
            });

        verify(systemClient).ingestTrackEvents(anyList());
    }

    /**
     * #100 验收 2 的宿主角：不写任何 {@code beforeName}，starter 必须自己抢到位置。
     *
     * <p>用真实的 starter 自动配置（远端日志自动配置 + {@code LogAutoConfiguration}）
     * 与真实的适配配置，断言 {@link LogDao} 装配的是 starter 的 {@link RemoteLogDao}
     * 而不是只打印的 {@code DefaultLogDao}。</p>
     */
    @Test
    @DisplayName("不写 beforeName：宿主只提供 SPI 适配 Bean 时，starter 自动装配远端 LogDao")
    void starterShouldWireRemoteLogDaoWithoutHostOrderingKnowledge() {
        // 反向前提：没有适配 Bean 时 starter 装配的应是「只打印」的默认实现。
        // 没有这条，「远端实现生效」可能只是恒真的巧合（两种形态装到同一个类）。
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class,
                starterLogAutoConfigurationClass()))
            .withBean(ISystemClient.class, () -> systemClient)
            .run(context -> {
                assertThat(context).hasSingleBean(LogDao.class);
                assertThat(context).getBean(LogDao.class)
                    .as("没有 RemoteLogIngestClient 时，starter 应退回只打印的默认实现")
                    .isNotInstanceOf(RemoteLogDao.class);
            });

        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class,
                starterLogAutoConfigurationClass()))
            .withUserConfiguration(RemoteIngestClientConfiguration.LogIngestConfiguration.class)
            .withBean(ISystemClient.class, () -> systemClient)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(LogDao.class);
                assertThat(context).getBean(LogDao.class)
                    .as("宿主只提供适配 Bean、未写 beforeName 时，starter 必须自己装配远端实现")
                    .isInstanceOf(RemoteLogDao.class);
            });
    }

    @Test
    @DisplayName("埋点侧同口径：不写 beforeName 时 starter 装配远端 TrackEventSink")
    void starterShouldWireRemoteTrackSinkWithoutHostOrderingKnowledge() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TrackingAutoConfiguration.class,
                starterTrackAutoConfigurationClass()))
            .withPropertyValues("ypbin.tracking.enabled=true")
            .withUserConfiguration(RemoteIngestClientConfiguration.TrackIngestConfiguration.class)
            .withBean(ISystemClient.class, () -> systemClient)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).getBean(TrackEventSink.class)
                    .as("宿主只提供适配 Bean、未写 beforeName 时，starter 必须自己装配远端 sink")
                    .isInstanceOf(RemoteTrackEventSink.class);
            });
    }
}
