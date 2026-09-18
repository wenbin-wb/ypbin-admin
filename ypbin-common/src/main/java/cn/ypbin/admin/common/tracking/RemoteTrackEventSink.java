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
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 埋点事件落点（跨服务上报实现）：把采集链路产出的事件上报给 system 服务落 {@code sys_track_event}。
 *
 * <p><strong>为什么是上报而不是直连库：</strong>auth 没有数据源（
 * {@code .claude/skills/ypbin-admin-dev/SKILL.md:21}：auth/ai 禁止直连共享库），而埋点的落库实现
 * {@code SysTrackEventSink} 在 system 服务里。若不提供本实现，starter 会装配只打印日志的
 * {@code LoggingTrackEventSink}（{@code TrackingAutoConfiguration.java:65-71} 会为此打 WARN）——
 * 事件只进应用日志、分析口径（漏斗/留存/趋势）永远查不到，正是本类要消除的静默失效。</p>
 *
 * <p><strong>调用线程</strong>：由 starter 的 {@code TrackFlusher} 消费者线程调用，与业务请求线程隔离，
 * 允许阻塞（一次 Feign 往返）。事件里的 IP/UA/链路 ID/用户等维度<strong>已由上报方在请求线程上捕获</strong>
 * （见 {@code TrackRequestContext} 的说明），本类不做二次补采——消费者线程也补不到。</p>
 *
 * <p><strong>异常语义</strong>：失败必须记完整堆栈并上抛，由采集链路计入失败计数、退避后重试一次
 * （{@code TrackFlusher}）。上抛不会波及业务请求：埋点链路与业务线程本就隔离。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public class RemoteTrackEventSink implements TrackEventSink {

    private static final Logger log = LoggerFactory.getLogger(RemoteTrackEventSink.class);

    private final ISystemClient systemClient;

    /**
     * 创建上报落点。
     *
     * @param systemClient system 服务 Feign 客户端
     */
    public RemoteTrackEventSink(ISystemClient systemClient) {
        this.systemClient = systemClient;
    }

    @Override
    public void write(List<TrackEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        R<Void> result;
        try {
            result = systemClient.ingestTrackEvents(events);
        } catch (RuntimeException ex) {
            // 传输层失败：连接/读取超时、熔断、响应解码异常。记完整堆栈后原样上抛（保留原始栈）
            log.error("[ypbin-admin] 埋点事件上报 system 服务失败（传输异常），本批 {} 条未落库: eventCodes={}",
                events.size(), codesOf(events), ex);
            throw ex;
        }
        if (result == null || !result.isSuccess()) {
            // 业务码失败（/internal 凭证不匹配、埋点未启用、system 侧异常）没有异常对象，显式构造以产出可定位堆栈
            IllegalStateException failure = new IllegalStateException(
                "[ypbin-admin] 埋点事件上报 system 服务失败，本批 " + events.size() + " 条未落库: "
                    + "code=" + (result == null ? "null" : result.getCode())
                    + ", message=" + (result == null ? "响应为空" : result.getMessage()));
            log.error("[ypbin-admin] 埋点事件上报 system 服务失败（业务码），eventCodes={}", codesOf(events), failure);
            throw failure;
        }
    }

    /**
     * 事件码摘要，仅用于日志定位（不含任何事件属性，避免把 payload 写进日志）。
     */
    private String codesOf(List<TrackEvent> events) {
        return events.stream().map(TrackEvent::eventCode).distinct().toList().toString();
    }
}
