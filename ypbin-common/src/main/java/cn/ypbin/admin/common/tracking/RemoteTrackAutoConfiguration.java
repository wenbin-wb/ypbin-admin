/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.tracking;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 埋点「跨服务上报」自动装配。
 *
 * <p><b>解决的问题：</b>starter 的 {@code TrackingAutoConfiguration.trackEventSink()} 在宿主没提供
 * {@link TrackEventSink} 时会装配只打印不落库的 {@code LoggingTrackEventSink}——auth 没有埋点落库实现，
 * 于是它采集到的登录/登出事件全部只进应用日志，{@code sys_track_event} 里查不到，漏斗里永远缺「登录」这一步。
 * 本配置为这类服务补上「上报给 system」的实现。</p>
 *
 * <p><b>装配优先级：</b>{@code beforeName} 指向 starter 的 {@code TrackingAutoConfiguration}，
 * 使 {@link #remoteTrackEventSink} 先于它的 {@code @ConditionalOnMissingBean} 判定完成注册；本方法自身也带
 * {@code @ConditionalOnMissingBean}，因此 <b>system</b>（已组件扫描出 {@code SysTrackEventSink}）与任何自带
 * 落库实现的宿主都会自动退让——这一点是硬要求：若 system 也拿到本实现，它的事件会 Feign 回自己的
 * {@code /internal/track-ingest}，形成自我回环并把每条事件重复写入。</p>
 *
 * <p><b>依赖可选：</b>本配置只在类路径同时存在 {@code TrackEventSink}（starter 埋点模块）与
 * {@link ISystemClient}（Feign 能力）时生效，没有埋点能力的应用不受影响。</p>
 *
 * <p><b>与日志上报的分工：</b>本类与 {@code RemoteLogAutoConfiguration} 同构，但两者互不依赖——
 * 操作日志（{@code sys_log}）与埋点事件（{@code sys_track_event}）是两套独立口径，不共用采集链路。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@AutoConfiguration(beforeName = "cn.ypbin.starter.tracking.autoconfigure.TrackingAutoConfiguration")
@ConditionalOnClass({TrackEventSink.class, ISystemClient.class})
@ConditionalOnProperty(prefix = "ypbin.tracking", name = "enabled", havingValue = "true")
public class RemoteTrackAutoConfiguration {

    /**
     * 无本地落库实现时，用「上报 system」替换 starter 的只打印默认实现。
     *
     * @param systemClient system 服务 Feign 客户端
     * @return 事件落点
     */
    @Bean
    @ConditionalOnMissingBean(TrackEventSink.class)
    public TrackEventSink remoteTrackEventSink(ISystemClient systemClient) {
        return new RemoteTrackEventSink(systemClient);
    }
}
