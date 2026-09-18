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

import cn.ypbin.admin.system.entity.SysTrackSession;
import cn.ypbin.admin.system.mapper.SysTrackSessionMapper;
import cn.ypbin.admin.system.mapper.SysTrackUserDailyMapper;
import cn.ypbin.admin.system.model.resp.TrackFunnelResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionPointResp;
import cn.ypbin.admin.system.model.resp.TrackRetentionResp;
import cn.ypbin.admin.system.service.TrackAnalysisService;
import cn.ypbin.admin.system.service.support.TrackEventSequenceBuilder;
import cn.ypbin.admin.system.service.support.TrackFunnelCalculator;
import cn.ypbin.admin.system.service.support.TrackQueryParams;
import cn.ypbin.admin.system.service.support.TrackRetentionMatrixBuilder;
import cn.ypbin.starter.core.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 埋点分析服务实现（只读，只查预聚合表）。
 *
 * <p>漏斗的「按序推进」与留存的「矩阵补零」都是纯逻辑，已抽到
 * {@link TrackFunnelCalculator} / {@link TrackRetentionMatrixBuilder} 单独测试；
 * 本类只负责取数、参数校验与边界保护。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Service
@RequiredArgsConstructor
public class TrackAnalysisServiceImpl implements TrackAnalysisService {

    /**
     * 单次漏斗最多装载的会话数。
     *
     * <p>超过即<strong>显式报错</strong>：漏斗要在内存里逐会话推进，无限装载会把堆打满。
     * 报错比截断好——截断出来的转化率是个看不出问题的错数字。</p>
     */
    private static final long FUNNEL_SESSION_LIMIT = 100000L;

    /** 探测溢出时多取一行：拿到「上限 +1」条即说明超限 */
    private static final long SESSION_QUERY_EXTRA = 1L;

    private final SysTrackSessionMapper trackSessionMapper;

    private final SysTrackUserDailyMapper trackUserDailyMapper;

    @Override
    public TrackFunnelResp funnel(String steps, int days) {
        TrackQueryParams.requireDays(days);
        List<String> stepCodes = TrackQueryParams.parseSteps(steps);
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        List<SysTrackSession> sessions = trackSessionMapper.selectSequencesByFirstStep(startTime, endTime,
            stepCodes.get(0), FUNNEL_SESSION_LIMIT + SESSION_QUERY_EXTRA);
        if (sessions.size() > FUNNEL_SESSION_LIMIT) {
            throw new BusinessException("漏斗窗口内的会话数超过上限 " + FUNNEL_SESSION_LIMIT
                + "，已中止（不做静默截断）；请缩小 days 或改用事件码更靠后的首步");
        }
        List<List<String>> sequences = sessions.stream()
            .map(session -> TrackEventSequenceBuilder.parse(session.getEventSequence()))
            .toList();
        TrackFunnelResp resp = new TrackFunnelResp();
        resp.setSteps(TrackFunnelCalculator.calculate(stepCodes, sequences));
        // 截断的会话可能丢了后续步骤、被当成「没走到该步」：各步会话数只是下限，必须一并暴露这个前提
        resp.setTruncatedSessionCount(TrackFunnelCalculator.countTruncatedSessions(
            sessions.stream().map(SysTrackSession::getTruncated).toList()));
        return resp;
    }

    @Override
    public TrackRetentionResp retention(int days) {
        TrackQueryParams.requireDays(days);
        LocalDate today = LocalDate.now();
        LocalDate startDate = TrackRetentionMatrixBuilder.earliestCohortDate(days, today);
        int maxOffset = TrackRetentionMatrixBuilder.maxOffset(days);
        List<TrackRetentionPointResp> points =
            trackUserDailyMapper.selectRetentionPoints(startDate, today, maxOffset);
        return TrackRetentionMatrixBuilder.build(days, today, points);
    }
}
