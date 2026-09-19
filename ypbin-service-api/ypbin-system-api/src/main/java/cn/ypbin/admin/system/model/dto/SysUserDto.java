/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.dto;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户身份快照（跨服务只读视图）。
 *
 * <p><b>为什么需要它</b>：{@code SysUser} 继承 {@code BaseEntity}/{@code TenantBaseEntity}，
 * 把实体暴露给 auth/ai 这类<b>无数据源</b>的服务，会让它们传递依赖 {@code ypbin-starter-data}
 * （MyBatis-Plus），违反本仓「auth/ai 不直连共享库」的约定。本类只承载登录流程真正需要的字段，
 * 且<b>字段名与实体逐一同名</b>（不做任何改名映射），转换发生在 {@code SysCache} 内部。</p>
 *
 * <p><b>不含密码</b>：缓存与跨服务传递的用户视图刻意不携带 {@code password}，
 * 密码校验一律走 {@code ISystemClient#verifyPassword} 直查库比对。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
@Getter
@Setter
public class SysUserDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID（对应实体主键） */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 真实姓名/显示名 */
    private String realName;

    /** 昵称（小程序等端侧展示名） */
    private String nickname;

    /** 头像地址 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 租户 ID */
    private Long tenantId;

    /** 部门 ID */
    private Long deptId;

    /** 状态（EntityStatus/UserStatusEnum 的 code，与实体字段同名同类型） */
    private Integer status;
}
