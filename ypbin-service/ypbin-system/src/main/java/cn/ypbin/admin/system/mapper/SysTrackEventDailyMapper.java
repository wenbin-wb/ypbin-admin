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

import cn.ypbin.admin.system.entity.SysTrackEventDaily;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 埋点事件按天聚合 Mapper。
 *
 * @author wenbin
 * @since 2026-09-16
 */
public interface SysTrackEventDailyMapper extends BaseMapper<SysTrackEventDaily> {

    /**
     * 批量写入一天的聚合行。
     *
     * <p><strong>刻意不写 {@code ON DUPLICATE KEY UPDATE}</strong>：调用方已先按天 {@code DELETE}，
     * 此时若仍撞唯一键，说明「删除窗口」与「写入窗口」口径不一致——这是真 bug，
     * 应当直接抛错暴露，而不是被 upsert 悄悄吸收。</p>
     *
     * @param rows 待写入行（调用方保证非空）
     * @return 受影响行数
     */
    @Insert("""
        <script>
        INSERT INTO sys_track_event_daily (stat_date, event_code, app_id, event_count, fail_count,
            duration_sum_ms, duration_cnt, create_time)
        VALUES
        <foreach collection="rows" item="item" separator=",">
            (#{item.statDate}, #{item.eventCode}, #{item.appId}, #{item.eventCount}, #{item.failCount},
             #{item.durationSumMs}, #{item.durationCnt}, #{item.createTime})
        </foreach>
        </script>
        """)
    int insertBatch(@Param("rows") List<SysTrackEventDaily> rows);
}
