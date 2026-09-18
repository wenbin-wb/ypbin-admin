/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.provider.DbLogProviders;
import cn.ypbin.admin.system.service.SocialBindService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.social.SocialConfigReader;
import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 内部端点 {@code GET /internal/platform-user} 的委派测试。
 *
 * <p>本端点存在的意义是「让 ai 复用 system 的平台用户判定口径」——它必须<b>只做路由</b>：
 * 判定条件、租户忽略都留在 {@code SysPermissionService#isPlatformUser} 一处。
 * 因此这里断言的就是「恰好委派一次、不夹带别的调用」，任何在端点里重写查询条件的改动都会露馅。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class SystemClientImplPlatformUserTest {

    private final SysPermissionService permissionService = mock(SysPermissionService.class);

    private final SystemClientImpl controller = new SystemClientImpl(
        permissionService,
        mock(SysUserService.class),
        mock(SysConfigMapper.class),
        mock(SocialConfigReader.class),
        mock(SocialBindService.class),
        mock(SysMenuService.class),
        new DbLogProviders.DbLogDao(mock(SysLogMapper.class)),
        providerOfTrackRecorder());

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TrackRecorder> providerOfTrackRecorder() {
        ObjectProvider<TrackRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(TrackRecorder.class));
        return provider;
    }

    @Test
    @DisplayName("判定为平台用户：返回成功 R 且 data=true，委派给 SysPermissionService 一次")
    void shouldDelegateAndReturnTrue() {
        when(permissionService.isPlatformUser(1L)).thenReturn(true);

        var response = controller.isPlatformUser(1L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isTrue();
        verify(permissionService).isPlatformUser(1L);
        verifyNoMoreInteractions(permissionService);
    }

    @Test
    @DisplayName("判定非平台用户：同样返回成功 R（判定已完成），data=false 由调用方决定拒绝")
    void shouldDelegateAndReturnFalseAsSuccessfulResponse() {
        when(permissionService.isPlatformUser(2L)).thenReturn(false);

        var response = controller.isPlatformUser(2L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isFalse();
        verify(permissionService).isPlatformUser(2L);
        verifyNoMoreInteractions(permissionService);
    }
}
