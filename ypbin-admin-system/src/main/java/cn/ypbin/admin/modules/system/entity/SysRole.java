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

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统角色。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称 */
    private String name;

    /** 角色标识（权限鉴权用，租户内唯一） */
    private String code;

    /** 角色类型：PLATFORM_SUPER 平台超级管理员、TENANT_ROLE 租户角色 */
    private String roleType;

    /** 数据范围：1 全部、2 本部门及以下、3 本部门、4 仅本人、5 自定义 */
    private Integer dataScope;

    /** 显示排序 */
    private Integer sort;

    /** 备注 */
    private String remark;
}
