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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysTrackEvent;
import cn.ypbin.admin.system.mapper.SysTrackEventMapper;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRequestContext;
import cn.ypbin.starter.tracking.core.TrackingEventCodes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 埋点落库映射测试（不依赖数据库：Mapper 用 Mockito 替身捕获待写入行）。
 *
 * <p>重点验证「采集上下文落到哪几列」——这是本次发布补齐 IP/UA/链路/用户/租户维度后的关键一环。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class SysTrackEventSinkTest {

    private final SysTrackEventMapper mapper = mock(SysTrackEventMapper.class);

    private final List<SysTrackEvent> captured = new ArrayList<>();

    private final SysTrackEventSink sink = new SysTrackEventSink(mapper);

    @BeforeEach
    void setUp() {
        when(mapper.insertBatch(anyList())).thenAnswer(invocation -> {
            List<SysTrackEvent> rows = invocation.getArgument(0);
            captured.addAll(rows);
            return rows.size();
        });
    }

    private static TrackEvent event(Boolean success, TrackRequestContext context) {
        return new TrackEvent("evt-1", TrackingEventCodes.UI_PAGE_VIEW,
            Instant.parse("2026-09-16T02:00:00Z"), "ypbin-admin-ui", "sess-1", "anon-1",
            "/system/user", "https://ref.example", 120L, success,
            Map.of("routeKey", "/system/user"), context);
    }

    @Test
    void shouldMapEventAndContextToColumns() {
        TrackRequestContext context = new TrackRequestContext("203.0.113.0", "UA/1.0", "trace-1", 7L, 1L);

        sink.write(List.of(event(Boolean.TRUE, context)));

        assertThat(captured).hasSize(1);
        SysTrackEvent row = captured.get(0);
        assertThat(row.getEventId()).isEqualTo("evt-1");
        assertThat(row.getEventCode()).isEqualTo(TrackingEventCodes.UI_PAGE_VIEW);
        assertThat(row.getAppId()).isEqualTo("ypbin-admin-ui");
        assertThat(row.getSessionId()).isEqualTo("sess-1");
        assertThat(row.getAnonId()).isEqualTo("anon-1");
        assertThat(row.getPageUrl()).isEqualTo("/system/user");
        assertThat(row.getReferrer()).isEqualTo("https://ref.example");
        assertThat(row.getDurationMs()).isEqualTo(120L);
        assertThat(row.getSuccess()).isEqualTo(1);
        assertThat(row.getPayload()).containsEntry("routeKey", "/system/user");
        // 采集上下文（服务端取值）必须落到对应列
        assertThat(row.getIp()).isEqualTo("203.0.113.0");
        assertThat(row.getUserAgent()).isEqualTo("UA/1.0");
        assertThat(row.getTraceId()).isEqualTo("trace-1");
        assertThat(row.getUserId()).isEqualTo(7L);
        assertThat(row.getTenantId()).isEqualTo(1L);
        // 时间：协议侧是 Instant，落库统一转 LocalDateTime
        assertThat(row.getEventTime()).isInstanceOf(LocalDateTime.class).isNotNull();
        assertThat(row.getReceivedTime()).isNotNull();
    }

    @Test
    void shouldKeepSuccessNullWhenUnknown() {
        sink.write(List.of(event(null, TrackRequestContext.EMPTY)));

        // 「无结果语义」不能伪造成失败
        assertThat(captured.get(0).getSuccess()).isNull();
    }

    @Test
    void shouldTolerateMissingContext() {
        TrackEvent withoutContext = new TrackEvent("evt-2", TrackingEventCodes.AUTH_USER_LOGIN,
            Instant.parse("2026-09-16T02:00:00Z"), null, null, null, null, null, 10L, Boolean.FALSE, Map.of());

        sink.write(List.of(withoutContext));

        SysTrackEvent row = captured.get(0);
        assertThat(row.getEventId()).isEqualTo("evt-2");
        assertThat(row.getSuccess()).isZero();
        assertThat(row.getIp()).isNull();
        assertThat(row.getUserId()).isNull();
    }

    @Test
    void shouldSkipEmptyBatch() {
        sink.write(List.of());

        verify(mapper, never()).insertBatch(anyList());
    }
}
