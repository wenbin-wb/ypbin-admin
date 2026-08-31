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
 * 系统日志分页查询条件。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class LogQuery extends PageQuery {

    /** 所属模块 */
    private String module;

    /** 描述（模糊） */
    private String description;

    /** 是否成功：1 成功、0 失败 */
    private Integer success;

    /** 操作人用户 ID */
    private Long operateUserId;

    /** 起始时间（yyyy-MM-dd HH:mm:ss） */
    private String startTime;

    /** 结束时间 */
    private String endTime;
}
