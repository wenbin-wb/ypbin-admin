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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.admin.system.provider.DbLogProviders;
import cn.ypbin.admin.system.service.SocialBindService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.social.SocialConfigReader;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 内部端点 {@code GET /internal/user-by-id} 的委派测试。
 *
 * <p><b>背景</b>：{@code /auth/social/callback/**} 在网关白名单内（匿名可访问），而 {@code X-Tenant-Id}
 * 只在登录后由网关签发。该端点原先调用继承自 {@code IService} 的 {@code getById}，租户拦截器
 * fail-closed（{@code sys_user} 不在 {@code ignore-tables}）会抛「缺少租户上下文」，第三方登录必然失败。</p>
 *
 * <p><b>本测试能证明什么</b>：端点必须委派给 service 层显式声明「忽略租户过滤」的方法
 * {@code getByIdGlobal}，而<b>不是</b>继承来的 {@code getById}。二者语义不同、名字相近，
 * 一旦被改回去（或有人「顺手」换成 {@code getById}）本测试立刻转红。
 * 「{@code getByIdGlobal} 确实打开了忽略作用域、{@code getById} 确实没有」由
 * {@code SysUserServiceImplTenantIgnoreTest} 用真实的 starter 租户处理器证明，二者合起来即完整链路。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
class SystemClientImplUserByIdTest {

    private static final Long USER_ID = 7L;

    private final SysUserService userService = mock(SysUserService.class);

    private final SystemClientImpl controller = new SystemClientImpl(
        mock(SysPermissionService.class),
        userService,
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
    @DisplayName("按 ID 取用户必须走「忽略租户过滤」的 service 方法，不得走受租户拦截器约束的 getById")
    void shouldDelegateToTenantIgnoringLookup() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        when(userService.getByIdGlobal(USER_ID)).thenReturn(user);

        R<SysUserDto> response = controller.getUserById(USER_ID);

        assertThat(response.isSuccess()).isTrue();
        // 契约返回视图而非实体：断言投影结果（不再是同一引用），委托行为由下方 verify 证明
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getId()).isEqualTo(USER_ID);
        verify(userService).getByIdGlobal(USER_ID);
        // 反向证明：走 getById 会在无租户上下文的匿名链路被 fail-closed 拦截
        verify(userService, never()).getById(any());
        verifyNoMoreInteractions(userService);
    }

    @Test
    @DisplayName("用户不存在时返回成功的空数据（委派已完成），由调用方决定提示")
    void shouldReturnNullDataWhenUserMissing() {
        when(userService.getByIdGlobal(USER_ID)).thenReturn(null);

        R<SysUserDto> response = controller.getUserById(USER_ID);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        verify(userService).getByIdGlobal(USER_ID);
    }
}
