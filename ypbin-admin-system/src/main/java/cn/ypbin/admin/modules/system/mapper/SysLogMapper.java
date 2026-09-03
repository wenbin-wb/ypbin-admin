/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.mapper;

import cn.ypbin.admin.modules.system.entity.SysLog;
import cn.ypbin.admin.modules.system.model.resp.LogTrendResp;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统日志 Mapper。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysLogMapper extends BaseMapper<SysLog> {

    /**
     * 按天聚合指定时间之后的操作日志条数（升序）。
     *
     * @param since 起始时间（含）
     * @return 每天一条 {date, count}，无日志的日期不返回，由上层补零
     */
    @Select("""
        SELECT DATE_FORMAT(operate_time, '%Y-%m-%d') AS `date`, COUNT(*) AS `count`
        FROM sys_log
        WHERE operate_time >= #{since}
        GROUP BY DATE_FORMAT(operate_time, '%Y-%m-%d')
        ORDER BY `date`
        """)
    List<LogTrendResp> selectDailyTrend(@Param("since") LocalDateTime since);
}
