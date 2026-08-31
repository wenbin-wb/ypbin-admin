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
 * 字典类型分页查询条件。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class DictQuery extends PageQuery {

    /** 字典名称（模糊） */
    private String name;

    /** 字典编码（模糊） */
    private String code;
}
