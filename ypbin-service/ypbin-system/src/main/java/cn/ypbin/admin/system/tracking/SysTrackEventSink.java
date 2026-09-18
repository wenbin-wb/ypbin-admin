/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.tracking;

import cn.ypbin.admin.system.entity.SysTrackEvent;
import cn.ypbin.admin.system.mapper.SysTrackEventMapper;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import cn.ypbin.starter.tracking.core.TrackRequestContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 埋点事件落库实现（starter 的 {@link TrackEventSink} 扩展点）。
 *
 * <p>由 starter 的采集链路在<strong>消费者线程</strong>上调用，与业务请求线程隔离，因此允许阻塞；
 * 批量写入靠单条多值 INSERT 完成，避免逐条往返。</p>
 *
 * <p><strong>时间约定</strong>：starter 侧协议事件时间用 {@link Instant}（见 iot/母仓的时间分界约定），
 * 落库统一转 {@link LocalDateTime}（GMT+8），转换只发生在本类这一处。</p>
 *
 * <p><strong>幂等</strong>：靠 {@code event_id} 唯一键 + {@code ON DUPLICATE KEY UPDATE id = id}
 * 实现（见 Mapper）；重复事件不会产生重复行，也不会让整批失败。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysTrackEventSink implements TrackEventSink {

    /** 结果标识：成功 */
    private static final int SUCCESS_FLAG = 1;

    /** 结果标识：失败 */
    private static final int FAIL_FLAG = 0;

    private final SysTrackEventMapper trackEventMapper;

    @Override
    public void write(List<TrackEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        LocalDateTime receivedTime = LocalDateTime.now();
        List<SysTrackEvent> rows = events.stream().map(event -> toEntity(event, receivedTime)).toList();
        int affected = trackEventMapper.insertBatch(rows);
        log.debug("[ypbin-admin] tracking events persisted, submitted={}, affected={}.", rows.size(), affected);
    }

    /**
     * 事件模型转实体；字段与实体同名，直接取值不做改名。
     *
     * @param event        采集链路产出的事件
     * @param receivedTime 本批的服务端接收时间（同批共用，避免逐条取时间造成批内时间抖动）
     * @return 待落库实体
     */
    private SysTrackEvent toEntity(TrackEvent event, LocalDateTime receivedTime) {
        SysTrackEvent entity = new SysTrackEvent();
        entity.setEventId(event.eventId());
        entity.setEventCode(event.eventCode());
        entity.setAppId(event.appId());
        entity.setSessionId(event.sessionId());
        entity.setAnonId(event.anonId());
        entity.setPageUrl(event.pageUrl());
        entity.setReferrer(event.referrer());
        entity.setEventTime(toLocalDateTime(event.eventTime()));
        entity.setReceivedTime(receivedTime);
        entity.setDurationMs(event.durationMs());
        entity.setSuccess(toSuccessFlag(event.success()));
        entity.setPayload(event.payload());
        TrackRequestContext context = event.context();
        if (context != null) {
            entity.setIp(context.clientIp());
            entity.setUserAgent(context.userAgent());
            entity.setTraceId(context.traceId());
            entity.setUserId(context.userId());
            entity.setTenantId(context.tenantId());
        }
        return entity;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private Integer toSuccessFlag(Boolean success) {
        if (success == null) {
            // 「无结果语义」与「失败」是两件事：为空一律留空，不伪造成失败
            return null;
        }
        return success ? SUCCESS_FLAG : FAIL_FLAG;
    }
}
