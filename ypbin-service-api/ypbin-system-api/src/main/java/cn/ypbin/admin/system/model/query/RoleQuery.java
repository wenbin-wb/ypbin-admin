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
 * 角色分页查询条件。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class RoleQuery extends PageQuery {

    /** 角色名称（模糊） */
    private String name;

    /** 角色标识（模糊） */
    private String code;

    /** 状态 */
    private Integer status;
}
