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

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限模板。定义一套可复用的菜单权限集合，分配给多个租户，租户登录后按其模板过滤可见菜单。
 * 全局表，不隔离租户。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_auth_template")
public class SysAuthTemplate extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板名称 */
    private String name;

    /** 模板编码（唯一） */
    private String code;

    /** 备注 */
    private String remark;
}
