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
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 埋点事件明细。字段与 starter 的 {@code TrackEvent} / {@code TrackRequestContext} 全程同名。
 *
 * <p>不继承 {@code BaseEntity}：本表由埋点采集链路的<strong>消费者线程</strong>写入，
 * 异步线程没有 Sa-Token 上下文，走审计字段自动填充会抛 {@code SaTokenContextException}。
 * 表自带 {@code received_time} 记录服务端接收时间，无需创建人/更新人字段。</p>
 *
 * <p>租户列 {@code tenant_id} 只作为<strong>归属记录</strong>：采集与落库都在无租户上下文的线程上，
 * 本表已登记在 {@code ypbin.tenant.ignore-tables}，不参与租户条件注入（否则 fail-closed 会直接抛错）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
@TableName(value = "sys_track_event", autoResultMap = true)
public class SysTrackEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，雪花算法生成 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 客户端事件唯一 ID（去重键） */
    private String eventId;

    /** 事件码：domain.object.action */
    private String eventCode;

    /** 应用标识 */
    private String appId;

    /** 用户 ID（采集时按登录身份补齐，未登录为空） */
    private Long userId;

    /** 租户 ID（采集时按租户上下文补齐） */
    private Long tenantId;

    /** 会话 ID */
    private String sessionId;

    /** 匿名标识 */
    private String anonId;

    /** 链路 ID（网关的 X-Request-Id） */
    private String traceId;

    /** 客户端事件时间（参考值，以 receivedTime 为准） */
    private LocalDateTime eventTime;

    /** 服务端接收时间（权威） */
    private LocalDateTime receivedTime;

    /** 页面地址（已去查询串） */
    private String pageUrl;

    /** 来源（已去查询串） */
    private String referrer;

    /** 客户端 IP（默认脱敏） */
    private String ip;

    /** User-Agent 原串 */
    private String userAgent;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 结果：1 成功、0 失败 */
    private Integer success;

    /** 事件属性（已按事件目录白名单裁剪） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;
}
