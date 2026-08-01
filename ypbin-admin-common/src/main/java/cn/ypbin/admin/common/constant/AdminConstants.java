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

    /** 超级管理员角色标识：拥有全部权限，跳过权限校验 */
    public static final String SUPER_ADMIN_ROLE = "super";

    /** 默认租户 ID */
    public static final Long DEFAULT_TENANT_ID = 1L;

    /** 顶级节点父 ID（菜单/部门根节点） */
    public static final Long ROOT_PARENT_ID = 0L;

    /** 通配权限码：拥有该权限视为拥有全部权限 */
    public static final String ALL_PERMISSION = "*:*:*";

    /** 后台管理登录客户端 ID */
    public static final String CLIENT_WEB_ADMIN = "web-admin";

    /** 账号密码认证方式 */
    public static final String AUTH_TYPE_ACCOUNT = "ACCOUNT";

    private AdminConstants() {
    }
}
