/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import java.util.List;

/**
 * 权限查询服务：集中用户的角色码、权限码、可见菜单查询。
 *
 * <p>这些查询关联租户隔离表（sys_role），且常在登录、鉴权等未建立完整租户上下文的时机触发，
 * 实现类统一用 {@code TenantContext.executeIgnore} 包裹，避免被行级租户拦截器加 {@code tenant_id=NULL}
 * 过滤成空集。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysPermissionService {

    /**
     * 查询用户拥有的权限码集合。超级管理员返回通配权限码。
     *
     * @param userId 用户 ID
     * @return 权限码列表
     */
    List<String> listPermissions(Long userId);

    /**
     * 查询用户拥有的角色码集合。
     *
     * @param userId 用户 ID
     * @return 角色码列表
     */
    List<String> listRoleCodes(Long userId);

    /**
     * 判断用户是否为平台用户。
     *
     * @param userId 用户 ID
     * @return 是否为正常且未删除的平台用户
     */
    boolean isPlatformUser(Long userId);

    /**
     * 判断用户是否为超级管理员。
     *
     * @param userId 用户 ID
     * @return 是否超管
     */
    boolean isSuperAdmin(Long userId);
}
