/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.mapper;

import cn.ypbin.admin.system.entity.SysTrackEvent;
import cn.ypbin.admin.system.entity.SysTrackEventDaily;
import cn.ypbin.admin.system.entity.SysTrackUserDaily;
import cn.ypbin.admin.system.model.resp.TrackAppCountResp;
import cn.ypbin.admin.system.model.resp.TrackOverviewResp;
import cn.ypbin.admin.system.model.resp.TrackTopEventResp;
import cn.ypbin.admin.system.model.resp.TrackTrendResp;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 埋点事件 Mapper。
 *
 * @author wenbin
 * @since 2026-09-16
 */
public interface SysTrackEventMapper extends BaseMapper<SysTrackEvent> {

    /**
     * 批量写入事件，并用 {@code ON DUPLICATE KEY UPDATE id = id} 实现幂等。
     *
     * <p><strong>为什么不是 {@code INSERT IGNORE}</strong>：{@code IGNORE} 会把数据截断、非法值等错误
     * 一并降级为告警（静默数据丢失）；{@code ON DUPLICATE KEY UPDATE} 只吞掉重复键，其余错误照旧抛出。</p>
     *
     * <p>重复键在此处是<strong>常态</strong>而非异常：客户端重试与离页兜底（sendBeacon）会与常规批量上报
     * 并发提交同一批事件。若用普通 INSERT，单条重复会让<strong>整批</strong>失败并丢弃。</p>
     *
     * <p>注解里写全限定类名是 MyBatis 的硬性要求（{@code typeHandler} 属性值由容器反射实例化，无法用 import
     * 表达）；这也是本仓唯一一处出现全限定类名的位置，特此说明。</p>
     *
     * @param events 待写入事件（调用方保证非空）
     * @return 受影响行数（MySQL 对「插入成功」计 1、「重复键未更新」计 0）
     */
    @Insert("""
        <script>
        INSERT INTO sys_track_event (id, event_id, event_code, app_id, user_id, tenant_id, session_id, anon_id,
            trace_id, event_time, received_time, page_url, referrer, ip, user_agent, duration_ms, success, payload)
        VALUES
        <foreach collection="events" item="item" separator=",">
            (#{item.id}, #{item.eventId}, #{item.eventCode}, #{item.appId}, #{item.userId}, #{item.tenantId},
             #{item.sessionId}, #{item.anonId}, #{item.traceId}, #{item.eventTime}, #{item.receivedTime},
             #{item.pageUrl}, #{item.referrer}, #{item.ip}, #{item.userAgent}, #{item.durationMs},
             #{item.success},
             #{item.payload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler})
        </foreach>
        ON DUPLICATE KEY UPDATE id = id
        </script>
        """)
    int insertBatch(@Param("events") List<SysTrackEvent> events);

    /**
     * 按天聚合事件数（升序）；无事件的日期不返回，由上层补零。
     *
     * @param since 起始时间（含）
     * @return 每天一条 {date, count}
     */
    @Select("""
        SELECT DATE_FORMAT(received_time, '%Y-%m-%d') AS `date`, COUNT(*) AS `count`
        FROM sys_track_event
        WHERE received_time >= #{since}
        GROUP BY DATE_FORMAT(received_time, '%Y-%m-%d')
        ORDER BY `date`
        """)
    List<TrackTrendResp> selectDailyTrend(@Param("since") LocalDateTime since);

    /**
     * 概览统计（一条 SQL 取全部计数，避免多次往返）。
     *
     * <p>用 {@code COALESCE} 兜住空表的 {@code SUM} 为 NULL 的情况；{@code COUNT(DISTINCT ...)} 只统计
     * 近 7 天且有用户标识的事件（匿名事件的 user_id 为空，不应计入活跃用户）。</p>
     *
     * @param todayStart 今日零点
     * @param weekStart  7 天前零点
     * @return 概览计数
     */
    @Select("""
        SELECT
            COUNT(*) AS totalEvents,
            COALESCE(SUM(CASE WHEN received_time >= #{todayStart} THEN 1 ELSE 0 END), 0) AS todayEvents,
            COALESCE(SUM(CASE WHEN received_time >= #{weekStart} THEN 1 ELSE 0 END), 0) AS weekEvents,
            COALESCE(SUM(CASE WHEN received_time >= #{weekStart} AND success = 0 THEN 1 ELSE 0 END), 0)
                AS weekFailures,
            COUNT(DISTINCT CASE WHEN received_time >= #{weekStart} THEN user_id END) AS weekUsers
        FROM sys_track_event
        """)
    TrackOverviewResp selectOverview(@Param("todayStart") LocalDateTime todayStart,
                                     @Param("weekStart") LocalDateTime weekStart);

    /**
     * 事件码排行（降序，取前 limit 个）。
     *
     * <p>只回事件码与次数：中文描述由前端按事件目录映射（目录的事实源在 starter 仓）。</p>
     *
     * @param since 起始时间（含）
     * @param limit 返回条数（调用方已校验上限）
     * @return 事件码与次数
     */
    @Select("""
        SELECT event_code, COUNT(*) AS `count`
        FROM sys_track_event
        WHERE received_time >= #{since}
        GROUP BY event_code
        ORDER BY `count` DESC
        LIMIT #{limit}
        """)
    List<TrackTopEventResp> selectTopEvents(@Param("since") LocalDateTime since, @Param("limit") int limit);

    /**
     * 应用维度分布（降序）。
     *
     * <p>事件未带 appId 时会聚出一行 app_id 为 NULL 的记录，由前端展示为"未设置"。</p>
     *
     * @param since 起始时间（含）
     * @return 应用与次数
     */
    @Select("""
        SELECT app_id, COUNT(*) AS `count`
        FROM sys_track_event
        WHERE received_time >= #{since}
        GROUP BY app_id
        ORDER BY `count` DESC
        """)
    List<TrackAppCountResp> selectAppDistribution(@Param("since") LocalDateTime since);

    /**
     * 按天聚合事件维度（事件聚合表的取数来源）。
     *
     * <p>{@code app_id} 用 {@code COALESCE(..., 哨兵)} 归一：聚合表的唯一键含 {@code app_id}，
     * 而 MySQL 唯一索引对 NULL 不去重，维度为空必须落成非空哨兵（见 {@code TrackDimensions}）。
     * {@code GROUP BY} 里的 {@code app_id} 是原始列，{@code COALESCE} 只是取值——
     * 同一组的 {@code app_id} 同值（含同为 NULL），归一结果唯一。</p>
     *
     * <p>{@code duration_cnt} 用 {@code COUNT(duration_ms)}：MySQL 的 {@code COUNT(列)} 不计 NULL，
     * 正是「有耗时的事件数」，与 {@code duration_sum_ms} 同源，可安全算平均。</p>
     *
     * <p>{@code GROUP BY} 带上 {@code DATE(received_time)} 是为了满足 {@code ONLY_FULL_GROUP_BY}
     * （MySQL 8 默认开启）；窗口本身就是一天，因此只会产出一组该日期。</p>
     *
     * <p>⚠️ 本 SQL <strong>无法在无数据库的 CI 中验证</strong>，部署后须用报告里给出的对照 SQL 核对。</p>
     *
     * @param start     起始时间（含）
     * @param end       结束时间（不含）
     * @param noneAppId 应用标识为空的哨兵值
     * @return 按 (日期, 事件码, 应用) 的聚合行
     */
    @Select("""
        SELECT DATE(received_time) AS stat_date,
               event_code,
               COALESCE(app_id, #{noneAppId}) AS app_id,
               COUNT(*) AS event_count,
               COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS fail_count,
               COALESCE(SUM(duration_ms), 0) AS duration_sum_ms,
               COUNT(duration_ms) AS duration_cnt
        FROM sys_track_event
        WHERE received_time >= #{start}
          AND received_time < #{end}
        GROUP BY DATE(received_time), event_code, app_id
        """)
    List<SysTrackEventDaily> selectEventDailyAggregate(@Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end,
                                                       @Param("noneAppId") String noneAppId);

    /**
     * 按天聚合用户维度（用户聚合表的取数来源）。
     *
     * <p>{@code user_id IS NOT NULL} 是硬条件：<strong>匿名事件不进用户聚合表</strong>，
     * 没有用户标识就无法判定「同一用户是否回来」。</p>
     *
     * <p>⚠️ 本 SQL <strong>无法在无数据库的 CI 中验证</strong>，部署后须用报告里给出的对照 SQL 核对。</p>
     *
     * @param start     起始时间（含）
     * @param end       结束时间（不含）
     * @param noneAppId 应用标识为空的哨兵值
     * @return 按 (日期, 用户, 应用) 的聚合行
     */
    @Select("""
        SELECT DATE(received_time) AS stat_date,
               user_id,
               COALESCE(app_id, #{noneAppId}) AS app_id,
               COUNT(*) AS event_count,
               MIN(received_time) AS first_time,
               MAX(received_time) AS last_time
        FROM sys_track_event
        WHERE received_time >= #{start}
          AND received_time < #{end}
          AND user_id IS NOT NULL
        GROUP BY DATE(received_time), user_id, app_id
        """)
    List<SysTrackUserDaily> selectUserDailyAggregate(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     @Param("noneAppId") String noneAppId);

    /**
     * 取窗口内有事件的会话 ID（会话重算的入口）。
     *
     * <p>调用方传入 {@code 上限 + 1} 作为 {@code limit}：拿到「多一条」即说明超出上限，
     * 由调用方显式报错——不做静默截断，截断会让聚合结果悄悄少算一部分会话。</p>
     *
     * @param start 起始时间（含）
     * @param end   结束时间（不含）
     * @param limit 返回行数上限
     * @return 会话 ID（升序）
     */
    @Select("""
        SELECT DISTINCT session_id
        FROM sys_track_event
        WHERE received_time >= #{start}
          AND received_time < #{end}
          AND session_id IS NOT NULL
        ORDER BY session_id
        LIMIT #{limit}
        """)
    List<String> selectSessionIds(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end,
                                  @Param("limit") long limit);

    /**
     * 取指定会话的<strong>全部</strong>明细事件（不设时间上界）。
     *
     * <p>不设时间上界是刻意的：跨天会话必须整段参与装配，否则会被算成两个半天，
     * 漏斗里「本会话是否按序完成」的判定就会失真。</p>
     *
     * <p>只取装配需要的列，不回 {@code payload} 大字段。</p>
     *
     * <p>调用方<strong>必须</strong>先判空短路：空集合会让 {@code IN ()} 变成语法错误。</p>
     *
     * @param sessionIds 会话 ID（非空）
     * @return 明细事件（按会话、接收时间、主键升序）
     */
    @Select("""
        <script>
        SELECT id, session_id, event_code, user_id, tenant_id, app_id, received_time
        FROM sys_track_event
        WHERE session_id IN
        <foreach collection="sessionIds" item="sessionId" open="(" separator="," close=")">
            #{sessionId}
        </foreach>
        ORDER BY session_id, received_time, id
        </script>
        """)
    List<SysTrackEvent> selectEventsBySessionIds(@Param("sessionIds") List<String> sessionIds);
}
