/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.aspect;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.modules.system.service.SysPermissionService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * {@link PlatformAccessAspect} 测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class PlatformAccessAspectTest {

    @Test
    void allowsCurrentPlatformUser() {
        SysPermissionService permissionService = mock(SysPermissionService.class);
        PlatformAccessAspect aspect = new PlatformAccessAspect(permissionService);
        try (MockedStatic<LoginHelper> loginHelper = Mockito.mockStatic(LoginHelper.class)) {
            loginHelper.when(LoginHelper::getUserId).thenReturn(1L);
            when(permissionService.isPlatformUser(1L)).thenReturn(true);

            assertThatCode(() -> aspect.guardClass(null)).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsCurrentNonPlatformUser() {
        SysPermissionService permissionService = mock(SysPermissionService.class);
        PlatformAccessAspect aspect = new PlatformAccessAspect(permissionService);
        try (MockedStatic<LoginHelper> loginHelper = Mockito.mockStatic(LoginHelper.class)) {
            loginHelper.when(LoginHelper::getUserId).thenReturn(2L);
            when(permissionService.isPlatformUser(2L)).thenReturn(false);

            assertThatThrownBy(() -> aspect.guardMethod(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅平台用户可访问");
        }
    }
}
