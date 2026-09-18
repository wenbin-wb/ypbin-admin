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

import cn.ypbin.admin.system.entity.SysTrackEvent;
import cn.ypbin.admin.system.entity.SysTrackSession;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由一个窗口内的明细事件装配出会话行。
 *
 * <p><strong>排序由本类负责，不依赖 SQL 的 ORDER BY</strong>：{@code received_time} 升序，
 * 同一毫秒用 {@code id} 兜底——同毫秒的先后只能靠主键（雪花算法单调）定序。
 * 把排序放在这里是为了让「顺序」这件事可被单测覆盖，而不是靠一条看不见的 SQL 契约。</p>
 *
 * <p><strong>归属字段取「最后一个非空值」</strong>：用户可能在会话中途登录，
 * 取最后一个非空值等价于「会话结束时已知的身份」；取第一个会把中途登录的会话算成匿名。</p>
 *
 * <p><strong>跨天会话</strong>：会话行覆盖整个会话生命周期，而不是被拆到各天。
 * 调用方必须先按窗口捞出会话 ID，再取这些会话的<strong>全部</strong>事件（不设时间上界），
 * 否则跨天会话会被算成两个半天。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackSessionAssembler {

    /** 截断标记：已截断（漏斗侧读取该标记用于提示「数字是下限」，故对外可见） */
    public static final int TRUNCATED_FLAG = 1;

    /** 截断标记：未截断 */
    private static final int NOT_TRUNCATED_FLAG = 0;

    /** 时间升序，同毫秒用主键兜底 */
    private static final Comparator<SysTrackEvent> ORDER = Comparator
        .comparing(SysTrackEvent::getReceivedTime, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(SysTrackEvent::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private TrackSessionAssembler() {
    }

    /**
     * 装配会话行。
     *
     * @param events     窗口内命中的会话的<strong>全部</strong>明细事件（顺序不限，本类自行排序）
     * @param createTime 聚合写入时间（由调用方传入，保证结果可重复）
     * @return 会话行；入参为空时返回空集合
     */
    public static List<SysTrackSession> assemble(List<SysTrackEvent> events, LocalDateTime createTime) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Map<String, List<SysTrackEvent>> grouped = new LinkedHashMap<>();
        for (SysTrackEvent event : events) {
            String sessionId = event.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                // 会话表的主键是 session_id，没有会话标识的事件无法归属；调用方已在 SQL 层过滤，
                // 这里再拦一次是为了不把「无会话事件」静默塞进某个会话
                throw new IllegalArgumentException("事件 id=" + event.getId() + " 没有 session_id，无法装配会话");
            }
            grouped.computeIfAbsent(sessionId, key -> new ArrayList<>()).add(event);
        }
        List<SysTrackSession> sessions = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<SysTrackEvent>> entry : grouped.entrySet()) {
            sessions.add(toSession(entry.getKey(), entry.getValue(), createTime));
        }
        return sessions;
    }

    private static SysTrackSession toSession(String sessionId, List<SysTrackEvent> events,
                                             LocalDateTime createTime) {
        List<SysTrackEvent> ordered = new ArrayList<>(events);
        ordered.sort(ORDER);
        List<String> eventCodes = ordered.stream().map(SysTrackEvent::getEventCode).toList();
        TrackEventSequenceBuilder.Sequence sequence = TrackEventSequenceBuilder.build(eventCodes);

        SysTrackEvent first = ordered.get(0);
        SysTrackEvent last = ordered.get(ordered.size() - 1);
        LocalDateTime startTime = requireReceivedTime(first);
        LocalDateTime endTime = requireReceivedTime(last);

        SysTrackSession session = new SysTrackSession();
        session.setSessionId(sessionId);
        session.setUserId(lastNonNull(ordered.stream().map(SysTrackEvent::getUserId).toList()));
        session.setTenantId(lastNonNull(ordered.stream().map(SysTrackEvent::getTenantId).toList()));
        session.setAppId(lastNonNull(ordered.stream().map(SysTrackEvent::getAppId).toList()));
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setDurationMs(Duration.between(startTime, endTime).toMillis());
        session.setEventCount((long) ordered.size());
        session.setEventSequence(sequence.value());
        session.setTruncated(sequence.truncated() ? TRUNCATED_FLAG : NOT_TRUNCATED_FLAG);
        session.setCreateTime(createTime);
        return session;
    }

    private static LocalDateTime requireReceivedTime(SysTrackEvent event) {
        LocalDateTime receivedTime = event.getReceivedTime();
        if (receivedTime == null) {
            // received_time 在明细表是 NOT NULL，为 null 说明数据或映射出了问题，显式报错而非静默跳过
            throw new IllegalArgumentException("事件 id=" + event.getId() + " 缺少 received_time，无法装配会话");
        }
        return receivedTime;
    }

    private static <T> T lastNonNull(List<T> values) {
        for (int index = values.size() - 1; index >= 0; index--) {
            if (values.get(index) != null) {
                return values.get(index);
            }
        }
        return null;
    }
}
