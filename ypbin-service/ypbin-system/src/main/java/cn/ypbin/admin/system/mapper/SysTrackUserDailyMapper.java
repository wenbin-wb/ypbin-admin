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

import cn.ypbin.admin.system.entity.SysTrackUserDaily;
import cn.ypbin.admin.system.model.resp.TrackRetentionPointResp;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 埋点用户按天聚合 Mapper（留存口径的事实源）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
public interface SysTrackUserDailyMapper extends BaseMapper<SysTrackUserDaily> {

    /**
     * 批量写入一天的用户聚合行。
     *
     * <p>与事件聚合同理，刻意不写 {@code ON DUPLICATE KEY UPDATE}：调用方已先按天 {@code DELETE}，
     * 再撞唯一键就是真 bug，应当抛错暴露。</p>
     *
     * @param rows 待写入行（调用方保证非空）
     * @return 受影响行数
     */
    @Insert("""
        <script>
        INSERT INTO sys_track_user_daily (stat_date, user_id, app_id, event_count, first_time, last_time,
            create_time)
        VALUES
        <foreach collection="rows" item="item" separator=",">
            (#{item.statDate}, #{item.userId}, #{item.appId}, #{item.eventCount}, #{item.firstTime},
             #{item.lastTime}, #{item.createTime})
        </foreach>
        </script>
        """)
    int insertBatch(@Param("rows") List<SysTrackUserDaily> rows);

    /**
     * 留存聚合点：每个用户的首次出现日，以及该日之后第 N 日又出现的去重人数。
     *
     * <p><strong>D0 取「全部历史里最早出现的那天」</strong>，因此内层子查询不做日期过滤——
     * 若只在窗口内取 {@code MIN(stat_date)}，窗口开始前就出现过的老用户会被当成新用户重复计入，
     * 留存率会系统性偏高。子查询由 {@code (user_id, stat_date)} 索引支撑。</p>
     *
     * <p>返回的是经典口径的「第 N 日留存」：只看第 N 日当天是否出现，不要求中间日期连续出现。</p>
     *
     * <p>⚠️ 本 SQL <strong>无法在无数据库的 CI 中验证</strong>，部署后须用报告里给出的对照 SQL 核对。</p>
     *
     * @param startDate 起始首次出现日（含）
     * @param endDate   结束首次出现日（含）
     * @param maxOffset 最大天数偏移
     * @return 聚合点（{@code cohortDate, dayOffset, userCount}）
     */
    @Select("""
        SELECT f.first_date AS cohort_date,
               DATEDIFF(t.stat_date, f.first_date) AS day_offset,
               COUNT(DISTINCT t.user_id) AS user_count
        FROM (
            SELECT user_id, MIN(stat_date) AS first_date
            FROM sys_track_user_daily
            GROUP BY user_id
        ) f
        JOIN sys_track_user_daily t
          ON t.user_id = f.user_id
         AND t.stat_date >= f.first_date
         AND t.stat_date <= DATE_ADD(f.first_date, INTERVAL #{maxOffset} DAY)
        WHERE f.first_date >= #{startDate}
          AND f.first_date <= #{endDate}
        GROUP BY f.first_date, DATEDIFF(t.stat_date, f.first_date)
        ORDER BY f.first_date, day_offset
        """)
    List<TrackRetentionPointResp> selectRetentionPoints(@Param("startDate") LocalDate startDate,
                                                        @Param("endDate") LocalDate endDate,
                                                        @Param("maxOffset") int maxOffset);
}
