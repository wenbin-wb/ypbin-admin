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

import cn.ypbin.starter.security.satoken.StpPermissionAdapter;

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

    /** 租户角色类型 */
    public static final String ROLE_TYPE_TENANT = "TENANT_ROLE";

    /** 顶级节点父 ID（菜单/部门根节点） */
    public static final Long ROOT_PARENT_ID = 0L;

    /**
     * 通配权限码：拥有该权限视为拥有全部权限。
     *
     * <p><strong>不再是本仓的字面量定义</strong>：该契约由 starter 单点定义
     * （{@code cn.ypbin.starter.security.satoken.StpPermissionAdapter#SUPER_ADMIN}），本仓只做引用，
     * 避免同一份契约两个定义点、漂移时静默越权/漏权。iot 侧已改为引用 starter 常量，本仓跟随。</p>
     */
    public static final String ALL_PERMISSION = StpPermissionAdapter.SUPER_ADMIN;

    /** 后台管理登录客户端 ID */
    public static final String CLIENT_WEB_ADMIN = "web-admin";

    private AdminConstants() {
    }
}
