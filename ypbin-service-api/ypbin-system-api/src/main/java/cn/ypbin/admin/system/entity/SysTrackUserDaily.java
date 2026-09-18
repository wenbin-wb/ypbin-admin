/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 埋点用户按天聚合，留存口径的事实源。
 *
 * <p><strong>匿名事件（{@code user_id} 为空）不写入本表</strong>：没有用户标识就无法判定「同一用户是否回来」，
 * 把匿名事件按 {@code anon_id} 混进来会得到一个既不是用户留存、也不是设备留存的数字。</p>
 *
 * <p>本表由聚合任务的线程写入，该线程没有 Sa-Token 上下文，
 * 因此<strong>不继承 {@code BaseEntity}</strong>（与 {@link SysTrackEvent} 同理），
 * 并已登记在 {@code ypbin.tenant.ignore-tables}。</p>
 *
 * <p><strong>本表没有代理主键</strong>：{@code (stat_date, user_id, app_id)} 既是主键也是唯一键，
 * 故 {@code equals/hashCode} 只基于这三列。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@TableName("sys_track_user_daily")
public class SysTrackUserDaily implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期 */
    @EqualsAndHashCode.Include
    private LocalDate statDate;

    /** 用户 ID */
    @EqualsAndHashCode.Include
    private Long userId;

    /** 应用标识（空值写哨兵） */
    @EqualsAndHashCode.Include
    private String appId;

    /** 当日事件数 */
    private Long eventCount;

    /** 当日最早事件时间 */
    private LocalDateTime firstTime;

    /** 当日最晚事件时间 */
    private LocalDateTime lastTime;

    /** 聚合写入时间 */
    private LocalDateTime createTime;
}
