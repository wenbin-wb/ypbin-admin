/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.query;

import cn.ypbin.starter.crud.model.PageQuery;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 埋点事件分页查询条件。
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class TrackEventQuery extends PageQuery {

    /** 事件码（精确匹配；为空表示不限） */
    private String eventCode;

    /** 应用标识（精确匹配） */
    private String appId;

    /** 用户 ID（精确匹配） */
    private Long userId;

    /** 会话 ID（精确匹配） */
    private String sessionId;

    /** 链路 ID（精确匹配，便于按一次请求串起多个事件） */
    private String traceId;

    /** 结果：1 成功、0 失败 */
    private Integer success;

    /** 起始时间（yyyy-MM-dd HH:mm:ss，按服务端接收时间过滤） */
    private String startTime;

    /** 结束时间（yyyy-MM-dd HH:mm:ss，按服务端接收时间过滤） */
    private String endTime;
}
