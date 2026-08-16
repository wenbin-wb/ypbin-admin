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
 * 用户-角色关联。
 *
 * <p>纯关联表，仅承载两个外键，不需要审计/逻辑删除，故不继承 BaseEntity。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}
