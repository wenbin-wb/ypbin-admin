/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.api.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.util.SpringUtils;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link SysCache} 单元测试。
 *
 * <p>验证：用户缓存回填前密码置空（安全）、角色/权限缓存、失效清理。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
class SysCacheTest {

    @BeforeEach
    void resetFeignClient() throws Exception {
        // SysCache.feignClient 是 static volatile，跨测试残留会导致 mock 串用；每次重置
        java.lang.reflect.Field field = SysCache.class.getDeclaredField("feignClient");
        field.setAccessible(true);
        field.set(null, null);
    }

    private SysUser buildUser() {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setUsername("alice");
        user.setPassword("encoded-secret");
        return user;
    }

    @Test
    void getUserByUsernameShouldSanitizePasswordBeforeCaching() {
        ISystemClient client = org.mockito.Mockito.mock(ISystemClient.class);
        SysUser user = buildUser();
        when(client.getUserByUsername("alice")).thenReturn(R.ok(user));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            // 模拟 getOrLoad：直接调 loader（密码置空发生在 loader 内）
            cacheUtils.when(() -> CacheUtils.getOrLoad(eq("sys:user:username:alice"), eq(SysUser.class), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(inv -> inv.getArgument(2, java.util.function.Supplier.class).get());

            SysUser cached = SysCache.getUserByUsername("alice");

            // 缓存回填前密码置空（缓存不落敏感字段）
            assertThat(cached.getPassword()).isNull();
            assertThat(cached.getUsername()).isEqualTo("alice");
        }
    }

    @Test
    void getUserByIdShouldSanitizePassword() {
        ISystemClient client = org.mockito.Mockito.mock(ISystemClient.class);
        SysUser user = buildUser();
        when(client.getUserById(42L)).thenReturn(R.ok(user));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            cacheUtils.when(() -> CacheUtils.getOrLoad(eq("sys:user:id:42"), eq(SysUser.class), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(inv -> inv.getArgument(2, java.util.function.Supplier.class).get());

            SysUser cached = SysCache.getUserById(42L);

            assertThat(cached.getPassword()).isNull();
        }
    }

    @Test
    void getUserRoleCodesShouldReturnEmptyListWhenFeignFails() {
        ISystemClient client = org.mockito.Mockito.mock(ISystemClient.class);
        when(client.listRoleCodes(42L)).thenReturn(R.fail(500, "不可用"));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            cacheUtils.when(() -> CacheUtils.getOrLoad(eq("sys:role:user:42"), any(), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(inv -> inv.getArgument(2, java.util.function.Supplier.class).get());

            List<String> roles = SysCache.getUserRoleCodes(42L);

            assertThat(roles).isEmpty();
        }
    }

    @Test
    void evictUserShouldDeleteIdAndUsernameKeys() {
        try (MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            cacheUtils.when(() -> CacheUtils.delete(any(String.class))).thenReturn(true);

            SysCache.evictUser(42L, "alice");

            cacheUtils.verify(() -> CacheUtils.delete("sys:user:id:42"));
            cacheUtils.verify(() -> CacheUtils.delete("sys:user:username:alice"));
        }
    }

    @Test
    void evictUserAuthShouldDeleteRoleAndPermKeys() {
        try (MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            cacheUtils.when(() -> CacheUtils.delete(any(java.util.Collection.class))).thenReturn(2L);

            SysCache.evictUserAuth(42L);

            cacheUtils.verify(() -> CacheUtils.delete(List.of("sys:role:user:42", "sys:perm:user:42")));
        }
    }

    @Test
    void getOrLoadShouldPassNullTtlForPermanentCache() {
        ISystemClient client = org.mockito.Mockito.mock(ISystemClient.class);
        when(client.getUserByUsername("alice")).thenReturn(R.ok(buildUser()));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            cacheUtils.when(() -> CacheUtils.getOrLoad(any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(2, java.util.function.Supplier.class).get());

            SysCache.getUserByUsername("alice");

            // 永久缓存：ttl 传 null（主动失效模式）
            cacheUtils.verify(() -> CacheUtils.getOrLoad(
                eq("sys:user:username:alice"), eq(SysUser.class), any(), org.mockito.ArgumentMatchers.isNull()));
        }
    }
}
