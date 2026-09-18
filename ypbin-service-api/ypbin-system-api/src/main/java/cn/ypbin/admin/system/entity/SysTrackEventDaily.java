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
 * 埋点事件按天 × 事件码 × 应用聚合。
 *
 * <p>本表由聚合任务的线程写入，该线程没有 Sa-Token 上下文，走审计字段自动填充会抛异常，
 * 因此<strong>不继承 {@code BaseEntity}</strong>（与 {@link SysTrackEvent} 同理），
 * 并已登记在 {@code ypbin.tenant.ignore-tables}。</p>
 *
 * <p><strong>本表没有代理主键</strong>：{@code (stat_date, event_code, app_id)} 既是主键也是唯一键，
 * 故 {@code equals/hashCode} 只基于这三列（等价于「只基于主键」）。</p>
 *
 * <p>{@code appId} 在库中为 {@code NOT NULL}：维度为空时写哨兵值而非 NULL——MySQL 唯一索引对 NULL 不去重，
 * 用 NULL 会让同一维度为空的多个分组各自成行。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@TableName("sys_track_event_daily")
public class SysTrackEventDaily implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期（按 received_time 的服务端时区分桶） */
    @EqualsAndHashCode.Include
    private LocalDate statDate;

    /** 事件码 */
    @EqualsAndHashCode.Include
    private String eventCode;

    /** 应用标识（空值写哨兵） */
    @EqualsAndHashCode.Include
    private String appId;

    /** 事件数 */
    private Long eventCount;

    /** 失败事件数（success = 0） */
    private Long failCount;

    /** 耗时求和（毫秒） */
    private Long durationSumMs;

    /** 耗时非空的事件数（用于算平均值） */
    private Long durationCnt;

    /** 聚合写入时间 */
    private LocalDateTime createTime;
}
