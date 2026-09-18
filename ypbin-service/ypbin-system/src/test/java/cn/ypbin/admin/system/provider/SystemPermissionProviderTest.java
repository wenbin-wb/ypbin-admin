/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * system 侧权限数据源测试：只做 loginId → userId 的转换与转发，不得对结果做任何过滤。
 *
 * <p>starter 的注解鉴权在宿主未提供 {@code PermissionProvider} 时用「返回空列表」的默认实现，
 * 因此本类是 {@code @SaCheckPermission} 能否生效的开关之一；而超管能否恒有全部权限，
 * 取决于本类是否<b>原样透传</b>底层返回的 {@code *:*:*}。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class SystemPermissionProviderTest {

    private final SysPermissionService permissionService = mock(SysPermissionService.class);

    private final SystemPermissionProvider provider = new SystemPermissionProvider(permissionService);

    @Test
    @DisplayName("超管：原样透传 *:*:*，不做任何过滤或截断（超管不掉权限）")
    void shouldPassThroughAllPermissionForSuperAdmin() {
        when(permissionService.listPermissions(1L)).thenReturn(List.of(AdminConstants.ALL_PERMISSION));

        List<String> permissions = provider.getPermissions("1", "login");

        assertThat(permissions).containsExactly("*:*:*");
        verify(permissionService).listPermissions(1L);
        verifyNoMoreInteractions(permissionService);
    }

    @Test
    @DisplayName("普通角色：原样返回菜单权限码集合")
    void shouldReturnMenuAuthCodesForNormalRole() {
        when(permissionService.listPermissions(42L))
            .thenReturn(List.of("system:role:list", "system:role:edit"));

        assertThat(provider.getPermissions(42L, "login"))
            .containsExactly("system:role:list", "system:role:edit");
    }

    @Test
    @DisplayName("角色码：原样返回底层角色码集合")
    void shouldReturnRoleCodes() {
        when(permissionService.listRoleCodes(42L)).thenReturn(List.of("tenant_admin"));

        assertThat(provider.getRoles("42", "login")).containsExactly("tenant_admin");
    }

    @Test
    @DisplayName("loginId 缺失：返回空集合（拒绝）且不发起查询")
    void shouldRejectMissingLoginId() {
        assertThat(provider.getPermissions(null, "login")).isEmpty();
        assertThat(provider.getRoles(null, "login")).isEmpty();

        verifyNoInteractions(permissionService);
    }

    @Test
    @DisplayName("loginId 非法：返回空集合（拒绝）且不发起查询")
    void shouldRejectIllegalLoginId() {
        assertThat(provider.getPermissions("not-a-number", "login")).isEmpty();

        verifyNoInteractions(permissionService);
    }

    @Test
    @DisplayName("查询失败：异常必须向上抛出，不得吞掉后返回空集合假装无权限")
    void shouldPropagateFailureInsteadOfSwallowing() {
        when(permissionService.listPermissions(42L)).thenThrow(new BusinessException("数据库不可用"));

        assertThatThrownBy(() -> provider.getPermissions("42", "login"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("数据库不可用");
    }
}
