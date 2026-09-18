/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.api.feign.ISystemClientFallback;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.util.SpringUtils;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * auth 侧权限数据源测试：经 {@code SysCache} 复用 system 的判定，且失败必须 fail-closed。
 *
 * <p>与 ai 侧同口径，锁定三点：按 {@code sys:perm:user:<id>} 走缓存且仅未命中才 Feign；
 * system 不可达时抛异常（不返回空集合、不放行）；loginId 缺失或非法时直接拒绝且不发 RPC。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class AuthPermissionProviderTest {

    private final AuthPermissionProvider provider = new AuthPermissionProvider();

    @BeforeEach
    void resetSysCacheFeignClient() throws Exception {
        // SysCache.feignClient 是 static volatile，跨测试残留会让 mock 串用（与 SysCacheTest 同口径）
        Field field = SysCache.class.getDeclaredField("feignClient");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    @DisplayName("命中：按 sys:perm:user:<id> 取权限码，未命中时 Feign 恰好调用一次")
    void shouldQuerySystemOnceAndReturnPermissions() {
        ISystemClient client = mock(ISystemClient.class);
        when(client.listPermissions(1001L)).thenReturn(R.ok(List.of("system:role:list")));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            stubCacheMiss(cacheUtils, "sys:perm:user:1001");

            assertThat(provider.getPermissions("1001", "login")).containsExactly("system:role:list");

            verify(client).listPermissions(1001L);
        }
    }

    @Test
    @DisplayName("角色码：按 sys:role:user:<id> 取角色码，未命中时 Feign 恰好调用一次")
    void shouldQuerySystemOnceAndReturnRoleCodes() {
        ISystemClient client = mock(ISystemClient.class);
        when(client.listRoleCodes(1001L)).thenReturn(R.ok(List.of("tenant_admin")));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            stubCacheMiss(cacheUtils, "sys:role:user:1001");

            assertThat(provider.getRoles(1001L, "login")).containsExactly("tenant_admin");

            verify(client).listRoleCodes(1001L);
        }
    }

    @Test
    @DisplayName("降级 fail-closed：Feign 降级返回失败 R 时必须抛异常，绝不返回空集合")
    void shouldFailLoudlyWhenSystemUnavailable() {
        ISystemClient client = mock(ISystemClient.class);
        ISystemClientFallback fallback = new ISystemClientFallback();
        when(client.listPermissions(2002L)).thenReturn(fallback.listPermissions(2002L));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            stubCacheMiss(cacheUtils, "sys:perm:user:2002");

            assertThatThrownBy(() -> provider.getPermissions(2002L, "login"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统服务暂不可用");
        }
    }

    @Test
    @DisplayName("响应成功但数据缺失：拒绝（空集合），不抛错也不放行")
    void shouldDenyWhenResponseHasNoData() {
        ISystemClient client = mock(ISystemClient.class);
        when(client.listRoleCodes(3003L)).thenReturn(R.ok(null));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            stubCacheMiss(cacheUtils, "sys:role:user:3003");

            assertThat(provider.getRoles(3003L, "login")).isEmpty();
        }
    }

    @Test
    @DisplayName("loginId 缺失或非法：拒绝且不发起 RPC")
    void shouldRejectIllegalLoginIdWithoutRemoteCall() {
        ISystemClient client = mock(ISystemClient.class);

        assertThat(provider.getPermissions(null, "login")).isEmpty();
        assertThat(provider.getPermissions("not-a-number", "login")).isEmpty();
        assertThat(provider.getRoles(null, "login")).isEmpty();

        verifyNoInteractions(client);
    }

    /**
     * 模拟缓存未命中：直接执行 loader，从而让 SysCache 内部的 Feign 调用真实发生。
     */
    private void stubCacheMiss(MockedStatic<CacheUtils> cacheUtils, String expectedKey) {
        cacheUtils.when(() -> CacheUtils.getOrLoad(eq(expectedKey), any(), any(), isNull()))
            .thenAnswer(invocation -> invocation.getArgument(2, Supplier.class).get());
    }
}
