/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.admin.system.entity.SysTrackEvent;
import cn.ypbin.admin.system.entity.SysTrackSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 会话装配测试（纯逻辑，不依赖数据库）。
 *
 * <p>重点覆盖三件容易出错的事：顺序（含同毫秒兜底）、截断标记、归属字段取值。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackSessionAssemblerTest {

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 9, 16, 1, 0, 0);

    @Test
    void shouldReturnEmptyWhenNoEvents() {
        assertThat(TrackSessionAssembler.assemble(List.of(), CREATE_TIME)).isEmpty();
    }

    @Test
    void shouldOrderByReceivedTimeThenId() {
        // 故意乱序传入，且前两条同毫秒——同毫秒只能靠主键定序
        List<SysTrackEvent> events = List.of(
            event("s1", "third", LocalDateTime.of(2026, 9, 15, 10, 0, 1), 3L),
            event("s1", "second", LocalDateTime.of(2026, 9, 15, 10, 0, 0), 2L),
            event("s1", "first", LocalDateTime.of(2026, 9, 15, 10, 0, 0), 1L));

        List<SysTrackSession> sessions = TrackSessionAssembler.assemble(events, CREATE_TIME);

        assertThat(sessions).hasSize(1);
        SysTrackSession session = sessions.get(0);
        assertThat(session.getEventSequence()).isEqualTo("first,second,third");
        assertThat(session.getEventCount()).isEqualTo(3L);
        assertThat(session.getStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 15, 10, 0, 0));
        assertThat(session.getEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 15, 10, 0, 1));
        assertThat(session.getDurationMs()).isEqualTo(1000L);
        assertThat(session.getTruncated()).isZero();
    }

    @Test
    void shouldSplitSessionsAndKeepLastNonNullOwner() {
        SysTrackEvent anonymous = event("s2", "a", LocalDateTime.of(2026, 9, 15, 10, 0, 0), 1L);
        SysTrackEvent loggedIn = event("s2", "b", LocalDateTime.of(2026, 9, 15, 10, 0, 1), 2L);
        loggedIn.setUserId(7L);
        loggedIn.setTenantId(1L);
        loggedIn.setAppId("ypbin-admin-ui");

        List<SysTrackSession> sessions =
            TrackSessionAssembler.assemble(List.of(anonymous, loggedIn), CREATE_TIME);

        assertThat(sessions).hasSize(1);
        // 会话中途登录时取最后一个非空值，否则会把登录用户算成匿名
        assertThat(sessions.get(0).getUserId()).isEqualTo(7L);
        assertThat(sessions.get(0).getTenantId()).isEqualTo(1L);
        assertThat(sessions.get(0).getAppId()).isEqualTo("ypbin-admin-ui");
    }

    @Test
    void shouldFlagTruncatedSession() {
        List<SysTrackEvent> events = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 9, 15, 10, 0, 0);
        for (int index = 0; index <= TrackEventSequenceBuilder.MAX_SEQUENCE_EVENTS; index++) {
            events.add(event("s1", "code-" + index, base.plusNanos(index), (long) index));
        }

        List<SysTrackSession> sessions = TrackSessionAssembler.assemble(events, CREATE_TIME);

        assertThat(sessions.get(0).getEventCount())
            .isEqualTo((long) TrackEventSequenceBuilder.MAX_SEQUENCE_EVENTS + 1);
        assertThat(sessions.get(0).getTruncated()).isEqualTo(1);
    }

    @Test
    void shouldRejectEventWithoutSessionId() {
        SysTrackEvent event = event(null, "a", LocalDateTime.of(2026, 9, 15, 10, 0, 0), 1L);

        assertThatThrownBy(() -> TrackSessionAssembler.assemble(List.of(event), CREATE_TIME))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("session_id");
    }

    @Test
    void shouldRejectEventWithoutReceivedTime() {
        SysTrackEvent event = event("s1", "a", null, 1L);

        assertThatThrownBy(() -> TrackSessionAssembler.assemble(List.of(event), CREATE_TIME))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("received_time");
    }

    private static SysTrackEvent event(String sessionId, String eventCode, LocalDateTime receivedTime, Long id) {
        SysTrackEvent event = new SysTrackEvent();
        event.setId(id);
        event.setSessionId(sessionId);
        event.setEventCode(eventCode);
        event.setReceivedTime(receivedTime);
        return event;
    }
}
