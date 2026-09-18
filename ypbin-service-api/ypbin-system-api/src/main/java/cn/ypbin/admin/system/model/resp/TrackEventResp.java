/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import cn.ypbin.starter.json.ref.RefText;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 埋点事件明细响应。
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackEventResp {

    /** 主键 */
    private Long id;

    /** 事件 ID（去重键） */
    private String eventId;

    /** 事件码 */
    private String eventCode;

    /** 应用标识 */
    private String appId;

    /** 用户 ID */
    @RefText("user")
    private Long userId;

    /** 会话 ID */
    private String sessionId;

    /** 匿名标识 */
    private String anonId;

    /** 链路 ID */
    private String traceId;

    /** 客户端事件时间 */
    private LocalDateTime eventTime;

    /** 服务端接收时间 */
    private LocalDateTime receivedTime;

    /** 页面地址 */
    private String pageUrl;

    /** 来源 */
    private String referrer;

    /** 客户端 IP */
    private String ip;

    /** User-Agent 原串 */
    private String userAgent;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 结果：1 成功、0 失败 */
    private Integer success;

    /** 事件属性 */
    private Map<String, Object> payload;
}
