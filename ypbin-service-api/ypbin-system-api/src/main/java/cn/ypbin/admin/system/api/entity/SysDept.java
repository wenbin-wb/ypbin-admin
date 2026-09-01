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

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统部门。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_dept")
public class SysDept extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父部门 ID，顶级为 0 */
    private Long pid;

    /** 部门名称 */
    private String name;

    /** 显示排序 */
    private Integer sort;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 备注 */
    private String remark;
}
