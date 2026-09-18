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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 埋点会话，漏斗查询的事实源。
 *
 * <p>{@code sessionId} 是主键：会话 ID 由客户端生成，天然去重，重算时按会话整行覆盖。
 * {@code userId}/{@code tenantId}/{@code appId} 只是归属记录、不参与任何唯一键，
 * 因此<strong>不需要哨兵值</strong>，空值按 NULL 原样保留（与两张按天聚合表不同）。</p>
 *
 * <p>{@code eventSequence} 是<strong>有界</strong>的有序事件码序列（最多
 * {@code TrackEventSequenceBuilder.MAX_SEQUENCE_EVENTS} 个，逗号分隔）：漏斗判定只读这一列，
 * 不再回表扫明细。超过上限即截断并把 {@code truncated} 置 1 —— 截断意味着
 * 「靠后的步骤可能实际发生过但未记录」，分析时必须把这个前提说清楚。</p>
 *
 * <p>本表由聚合任务的线程写入，该线程没有 Sa-Token 上下文，
 * 因此<strong>不继承 {@code BaseEntity}</strong>（与 {@link SysTrackEvent} 同理），
 * 并已登记在 {@code ypbin.tenant.ignore-tables}。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@TableName("sys_track_session")
public class SysTrackSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话 ID（客户端生成） */
    @TableId(value = "session_id", type = IdType.INPUT)
    @EqualsAndHashCode.Include
    private String sessionId;

    /** 用户 ID（未登录为空） */
    private Long userId;

    /** 租户 ID */
    private Long tenantId;

    /** 应用标识 */
    private String appId;

    /** 会话内最早事件时间 */
    private LocalDateTime startTime;

    /** 会话内最晚事件时间 */
    private LocalDateTime endTime;

    /** 会话时长（毫秒） */
    private Long durationMs;

    /** 会话内事件数 */
    private Long eventCount;

    /** 有序事件码序列（最多 50 个，逗号分隔） */
    private String eventSequence;

    /** 1 表示事件数超过上限被截断 */
    private Integer truncated;

    /** 聚合写入时间 */
    private LocalDateTime createTime;
}
