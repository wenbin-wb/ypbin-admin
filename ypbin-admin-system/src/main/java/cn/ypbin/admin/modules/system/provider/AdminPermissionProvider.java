/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.provider;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.modules.system.service.SysPermissionService;
import cn.ypbin.starter.security.core.PermissionProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 权限数据源：向 Sa-Token 提供当前用户的权限码与角色码。
 *
 * <p>接通 {@code @SaCheckPermission}/{@code @SaCheckRole} 与 CrudController 的 {@code permissionPrefix}。
 * 超级管理员返回通配权限码 {@link AdminConstants#ALL_PERMISSION}，Sa-Token 据此放行全部权限校验。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Component
@RequiredArgsConstructor
public class AdminPermissionProvider implements PermissionProvider {

    private final SysPermissionService permissionService;

    @Override
    public List<String> getPermissions(Object loginId, String loginType) {
        return permissionService.listPermissions(toUserId(loginId));
    }

    @Override
    public List<String> getRoles(Object loginId, String loginType) {
        return permissionService.listRoleCodes(toUserId(loginId));
    }

    private Long toUserId(Object loginId) {
        return Long.valueOf(loginId.toString());
    }
}
