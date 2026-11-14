/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.constant;

/**
 * 系统通用常量。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public final class AdminConstants {

    /** 超级管理员角色标识 */
    public static final String SUPER_ADMIN_ROLE = "super";

    /** 平台用户类型 */
    public static final String USER_TYPE_PLATFORM = "PLATFORM";

    /** 租户用户类型 */
    public static final String USER_TYPE_TENANT = "TENANT";

    /** 平台超级管理员角色类型 */
    public static final String ROLE_TYPE_PLATFORM_SUPER = "PLATFORM_SUPER";

    /** 租户角色类型 */
    public static final String ROLE_TYPE_TENANT = "TENANT_ROLE";

    /** 默认租户 ID */
    public static final Long DEFAULT_TENANT_ID = 1L;

    /** 顶级节点父 ID（菜单/部门根节点） */
    public static final Long ROOT_PARENT_ID = 0L;

    /** 通配权限码：拥有该权限视为拥有全部权限 */
    public static final String ALL_PERMISSION = "*:*:*";

    /**
     * 登录请求头：客户端声明自己是谁，服务端据此选择 sys_client 中的策略。
     *
     * <p>不在这里枚举各端 ID：新增应用只需在「客户端管理」里登记一行并让客户端带上该头，
     * 后端不需要改代码；未声明时回落到配置的默认客户端。</p>
     */
    public static final String CLIENT_ID_HEADER = "X-Client-Id";

    /**
     * 登录请求头：客户端密钥。仅对在「客户端管理」里配置了密钥的客户端生效；
     * 浏览器、小程序这类无法保管密钥的端应保持客户端密钥为空，不要下发该头。
     *
     * <p>它校验的是「自报身份」的客户端，不构成身份认证，也不能阻止调用方改报另一个客户端 ID。</p>
     */
    public static final String CLIENT_SECRET_HEADER = "X-Client-Secret";

    /** 账号密码认证方式 */
    public static final String AUTH_TYPE_ACCOUNT = "ACCOUNT";

    private AdminConstants() {
    }
}
