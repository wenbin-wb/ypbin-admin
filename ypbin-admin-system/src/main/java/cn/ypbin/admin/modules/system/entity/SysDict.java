/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据字典类型。全局共享，不隔离租户，故继承 {@link BaseEntity}。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_dict")
public class SysDict extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 字典名称 */
    private String name;

    /** 字典编码（唯一，即字典类型 dictType） */
    private String code;

    /** 备注 */
    private String remark;
}
