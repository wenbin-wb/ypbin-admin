/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysTrackSession;
import cn.ypbin.admin.system.mapper.SysTrackSessionMapper;
import cn.ypbin.admin.system.mapper.SysTrackUserDailyMapper;
import cn.ypbin.admin.system.model.resp.TrackFunnelResp;
import cn.ypbin.admin.system.service.support.TrackSessionAssembler;
import cn.ypbin.starter.core.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 漏斗查询装配测试（Mapper 用 mock，不依赖数据库）。
 *
 * <p>覆盖「响应必须带上被截断的会话数」这一前提的装配：漏掉它，接口会把下限当成精确值展示。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackAnalysisServiceImplTest {

    private final SysTrackSessionMapper trackSessionMapper = mock(SysTrackSessionMapper.class);

    private final SysTrackUserDailyMapper trackUserDailyMapper = mock(SysTrackUserDailyMapper.class);

    private final TrackAnalysisServiceImpl service =
        new TrackAnalysisServiceImpl(trackSessionMapper, trackUserDailyMapper);

    @Test
    void shouldExposeTruncatedSessionCount() {
        // 参与漏斗判定的两个会话里，有一个的 event_sequence 被截断
        when(trackSessionMapper.selectSequencesByFirstStep(any(LocalDateTime.class), any(LocalDateTime.class),
            eq("a"), anyLong()))
            .thenReturn(List.of(session("a,b,c", TrackSessionAssembler.TRUNCATED_FLAG), session("a,b", 0)));

        TrackFunnelResp resp = service.funnel("a,b,c", 7);

        assertThat(resp.getSteps()).hasSize(3);
        assertThat(resp.getSteps().get(0).getSessionCount()).isEqualTo(2L);
        assertThat(resp.getSteps().get(1).getSessionCount()).isEqualTo(2L);
        assertThat(resp.getSteps().get(2).getSessionCount()).isEqualTo(1L);
        assertThat(resp.getTruncatedSessionCount()).isEqualTo(1L);
    }

    @Test
    void shouldAbortInsteadOfSilentlyTruncatingWhenSessionLimitExceeded() {
        SysTrackSession one = session("a", 0);
        when(trackSessionMapper.selectSequencesByFirstStep(any(LocalDateTime.class), any(LocalDateTime.class),
            eq("a"), anyLong()))
            .thenReturn(Stream.generate(() -> one).limit(100_001L).toList());

        // 不做静默截断：超上限必须显式报错，否则算出来的是个看不出问题的错数字
        assertThatThrownBy(() -> service.funnel("a,b", 7))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("超过上限");
    }

    private static SysTrackSession session(String sequence, int truncated) {
        SysTrackSession session = new SysTrackSession();
        session.setSessionId("s-" + sequence + '-' + truncated);
        session.setEventSequence(sequence);
        session.setTruncated(truncated);
        return session;
    }
}
