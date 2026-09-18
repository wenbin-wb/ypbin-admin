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

import cn.ypbin.admin.system.entity.SysTrackEvent;
import cn.ypbin.admin.system.entity.SysTrackEventDaily;
import cn.ypbin.admin.system.entity.SysTrackSession;
import cn.ypbin.admin.system.entity.SysTrackUserDaily;
import cn.ypbin.admin.system.mapper.SysTrackEventDailyMapper;
import cn.ypbin.admin.system.mapper.SysTrackEventMapper;
import cn.ypbin.admin.system.mapper.SysTrackSessionMapper;
import cn.ypbin.admin.system.mapper.SysTrackUserDailyMapper;
import cn.ypbin.admin.system.service.TrackAggregateService;
import cn.ypbin.admin.system.service.support.TrackDimensions;
import cn.ypbin.admin.system.service.support.TrackSessionAssembler;
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 埋点聚合服务实现。
 *
 * <p><strong>幂等策略：按天重算</strong>——先删除该天（会话表按会话 ID 删）再整段写入，
 * 不做增量水位。事件会延迟到达（消费者批量落库、客户端补发、离页兜底），
 * 增量会永久漏算；重算只会重复劳动，不会算错。</p>
 *
 * <p><strong>为什么把 SELECT 也放进事务</strong>：删除与写入之间不允许别的写者插进来，
 * 否则会写出一份混了两个时间点的聚合结果。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackAggregateServiceImpl implements TrackAggregateService {

    /**
     * 单日参与重算的会话数上限。
     *
     * <p>超过即<strong>显式报错</strong>而不是截断：截断会让聚合结果悄悄少算一部分会话，
     * 而使用者会把它当成全量数字。上限存在的意义是把「一次要拉多少会话的明细进内存」
     * 这件事摆在明面上，需要时再调大或改成按会话分批。</p>
     */
    private static final long SESSION_REBUILD_LIMIT = 20000L;

    /** 探测溢出时多取一行：拿到「上限 +1」条即说明超限 */
    private static final long SESSION_QUERY_EXTRA = 1L;

    private final SysTrackEventMapper trackEventMapper;

    private final SysTrackEventDailyMapper trackEventDailyMapper;

    private final SysTrackUserDailyMapper trackUserDailyMapper;

    private final SysTrackSessionMapper trackSessionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildDate(LocalDate statDate) {
        LocalDateTime start = statDate.atStartOfDay();
        LocalDateTime end = statDate.plusDays(1).atStartOfDay();
        // 同一天的三张表共用一个写入时间，便于排查「这批数字是哪一次跑出来的」
        LocalDateTime createTime = LocalDateTime.now();
        int eventRows = rebuildEventDaily(statDate, start, end, createTime);
        int userRows = rebuildUserDaily(statDate, start, end, createTime);
        int sessionRows = rebuildSessions(start, end, createTime);
        log.info("[ypbin-admin] tracking aggregate rebuilt, statDate={}, eventRows={}, userRows={}, sessionRows={}.",
            statDate, eventRows, userRows, sessionRows);
    }

    private int rebuildEventDaily(LocalDate statDate, LocalDateTime start, LocalDateTime end,
                                  LocalDateTime createTime) {
        List<SysTrackEventDaily> rows =
            trackEventMapper.selectEventDailyAggregate(start, end, TrackDimensions.NONE_APP_ID);
        trackEventDailyMapper.delete(new LambdaQueryWrapper<SysTrackEventDaily>()
            .eq(SysTrackEventDaily::getStatDate, statDate));
        if (rows.isEmpty()) {
            return 0;
        }
        rows.forEach(row -> row.setCreateTime(createTime));
        trackEventDailyMapper.insertBatch(rows);
        return rows.size();
    }

    private int rebuildUserDaily(LocalDate statDate, LocalDateTime start, LocalDateTime end,
                                 LocalDateTime createTime) {
        List<SysTrackUserDaily> rows =
            trackEventMapper.selectUserDailyAggregate(start, end, TrackDimensions.NONE_APP_ID);
        trackUserDailyMapper.delete(new LambdaQueryWrapper<SysTrackUserDaily>()
            .eq(SysTrackUserDaily::getStatDate, statDate));
        if (rows.isEmpty()) {
            return 0;
        }
        rows.forEach(row -> row.setCreateTime(createTime));
        trackUserDailyMapper.insertBatch(rows);
        return rows.size();
    }

    /**
     * 重算窗口内命中过的会话。
     *
     * <p>先按 {@code received_time} 捞出窗口内活跃的会话 ID，再取这些会话的<strong>全部</strong>事件——
     * 跨天会话必须整段重算，否则会被算成两个半天。</p>
     *
     * @param start      窗口起点（含）
     * @param end        窗口终点（不含）
     * @param createTime 聚合写入时间
     * @return 写入的会话行数
     */
    private int rebuildSessions(LocalDateTime start, LocalDateTime end, LocalDateTime createTime) {
        List<String> sessionIds =
            trackEventMapper.selectSessionIds(start, end, SESSION_REBUILD_LIMIT + SESSION_QUERY_EXTRA);
        if (sessionIds.isEmpty()) {
            return 0;
        }
        if (sessionIds.size() > SESSION_REBUILD_LIMIT) {
            throw new BusinessException("单日活跃会话数超过上限 " + SESSION_REBUILD_LIMIT
                + "，会话聚合已中止（不做静默截断）；请调大上限或改为按会话分批重算");
        }
        List<SysTrackEvent> events = trackEventMapper.selectEventsBySessionIds(sessionIds);
        List<SysTrackSession> sessions = TrackSessionAssembler.assemble(events, createTime);
        trackSessionMapper.deleteBySessionIds(sessionIds);
        if (sessions.isEmpty()) {
            // 上面已用非空 sessionIds 查过事件，正常不可能为空；这里判空是为了不给批量 INSERT 传空集合
            return 0;
        }
        trackSessionMapper.insertBatch(sessions);
        return sessions.size();
    }
}
