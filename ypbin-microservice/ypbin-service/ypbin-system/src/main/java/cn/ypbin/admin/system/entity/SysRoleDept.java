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

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 角色-部门关联。用于自定义数据范围（data_scope=5），将角色绑定到具体部门。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_dept")
public class SysRoleDept implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 ID */
    private Long roleId;

    /** 部门 ID */
    private Long deptId;
}
