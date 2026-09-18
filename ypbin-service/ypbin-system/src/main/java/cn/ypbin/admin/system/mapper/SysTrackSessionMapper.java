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

import cn.ypbin.admin.system.entity.SysTrackSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 埋点会话 Mapper（漏斗查询的事实源）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
public interface SysTrackSessionMapper extends BaseMapper<SysTrackSession> {

    /**
     * 批量写入会话行。
     *
     * <p>刻意不写 {@code ON DUPLICATE KEY UPDATE}：调用方已先按 {@code session_id} 整行删除，
     * 再撞主键就是真 bug。</p>
     *
     * @param rows 待写入行（调用方保证非空）
     * @return 受影响行数
     */
    @Insert("""
        <script>
        INSERT INTO sys_track_session (session_id, user_id, tenant_id, app_id, start_time, end_time,
            duration_ms, event_count, event_sequence, truncated, create_time)
        VALUES
        <foreach collection="rows" item="item" separator=",">
            (#{item.sessionId}, #{item.userId}, #{item.tenantId}, #{item.appId}, #{item.startTime},
             #{item.endTime}, #{item.durationMs}, #{item.eventCount}, #{item.eventSequence},
             #{item.truncated}, #{item.createTime})
        </foreach>
        </script>
        """)
    int insertBatch(@Param("rows") List<SysTrackSession> rows);

    /**
     * 按会话 ID 批量删除（重算前先清掉旧行）。
     *
     * @param sessionIds 会话 ID（调用方保证非空）
     * @return 删除行数
     */
    @Delete("""
        <script>
        DELETE FROM sys_track_session
        WHERE session_id IN
        <foreach collection="sessionIds" item="sessionId" open="(" separator="," close=")">
            #{sessionId}
        </foreach>
        </script>
        """)
    int deleteBySessionIds(@Param("sessionIds") List<String> sessionIds);

    /**
     * 取漏斗窗口内的会话序列。
     *
     * <p>{@code FIND_IN_SET} 先把「连第一步都没出现的会话」挡在数据库里：漏斗的第一步通常只覆盖
     * 少数会话，全量把会话序列读进内存没有必要。它要求事件码里不含逗号——序列的生成侧
     * （{@code TrackEventSequenceBuilder}）已对含逗号的事件码显式报错，两侧口径一致。</p>
     *
     * <p>{@code ORDER BY session_id} 是为了让 {@code LIMIT} 的结果稳定，
     * 从而让「超出上限」这件事可复现而不是随机截断。</p>
     *
     * <p>⚠️ 本 SQL <strong>无法在无数据库的 CI 中验证</strong>，部署后须用报告里给出的对照 SQL 核对。</p>
     *
     * @param startTime  会话起始时间下界（含）
     * @param endTime    会话起始时间上界（不含）
     * @param firstStep  漏斗第一步的事件码
     * @param limit      返回行数上限（调用方传上限 +1 用于探测溢出）
     * @return 会话序列（只取漏斗需要的三列）
     */
    @Select("""
        SELECT session_id, event_sequence, truncated
        FROM sys_track_session
        WHERE start_time >= #{startTime}
          AND start_time < #{endTime}
          AND event_sequence IS NOT NULL
          AND FIND_IN_SET(#{firstStep}, event_sequence) > 0
        ORDER BY session_id
        LIMIT #{limit}
        """)
    List<SysTrackSession> selectSequencesByFirstStep(@Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime,
                                                      @Param("firstStep") String firstStep,
                                                      @Param("limit") long limit);
}
