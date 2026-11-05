/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.query;

import cn.ypbin.starter.crud.model.PageQuery;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 访问黑名单分页查询条件。
 *
 * @author wenbin
 * @since 2026-11-04
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class AccessBlocklistQuery extends PageQuery {

    /** 匹配维度 */
    private String matchType;

    /** 匹配值（模糊） */
    private String matchValue;

    /** 状态：1 启用、0 停用 */
    private Integer status;
}
