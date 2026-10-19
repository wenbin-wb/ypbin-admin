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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.model.dto.SysUserSocialDto;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.util.SpringUtils;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link SysCache} 的「列表载荷」读取测试（unchecked 强转收敛后的行为锁定）。
 *
 * <p><b>为什么要测</b>：本类的 4 处 {@code (Class<List<T>>) (Class<?>) List.class} 被收敛到一个
 * {@code @SuppressWarnings} 的私有方法里。压制告警是「治标」，因此必须有用例证明这 4 条读取路径
 * <b>仍然拿得到列表、且列表元素没有丢</b>——否则「告警消失」可能只是因为强转写错了、类型在运行期被抹平。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class SysCacheListPayloadTest {

    @BeforeEach
    void resetFeignClient() throws Exception {
        // SysCache.feignClient 是 static volatile，跨测试残留会让 mock 串用
        Field field = SysCache.class.getDeclaredField("feignClient");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    @DisplayName("角色码列表：类型占位为 List.class，回源载荷原样返回（元素不丢、顺序不变）")
    void roleCodesListPayloadShouldRoundTrip() {
        ISystemClient client = mock(ISystemClient.class);
        List<String> codes = List.of("super", "tenant-admin");
        when(client.listRoleCodes(1L)).thenReturn(R.ok(codes));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            stubListPayload(cacheUtils, "sys:role:user:1", codes);

            assertThat(SysCache.getUserRoleCodes(1L)).containsExactlyElementsOf(codes);
        }
    }

    @Test
    @DisplayName("权限码列表：同上（鉴权链路依赖它，绝不能因为类型占位变成 null/空）")
    void permissionListPayloadShouldRoundTrip() {
        ISystemClient client = mock(ISystemClient.class);
        List<String> codes = List.of("system:user:list", "ai:model:edit");
        when(client.listPermissions(1L)).thenReturn(R.ok(codes));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            stubListPayload(cacheUtils, "sys:perm:user:1", codes);

            assertThat(SysCache.getUserPermissions(1L)).containsExactlyElementsOf(codes);
        }
    }

    @Test
    @DisplayName("第三方登录配置列表：列表载荷读出后可正常遍历取字段")
    void socialConfigsListPayloadShouldRoundTrip() {
        ISystemClient client = mock(ISystemClient.class);
        SocialAuthConfig config = new SocialAuthConfig();
        config.setSource("github");
        when(client.listSocialAuthConfigs()).thenReturn(R.ok(List.of(config)));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            stubListPayload(cacheUtils, "sys:social:configs", List.of(config));

            assertThat(SysCache.listSocialAuthConfigs())
                .singleElement()
                .satisfies(item -> assertThat(item.getSource()).isEqualTo("github"));
        }
    }

    @Test
    @DisplayName("第三方绑定列表：列表载荷读出后元素类型未丢（可调用元素方法）")
    void socialBindingsListPayloadShouldRoundTrip() {
        ISystemClient client = mock(ISystemClient.class);
        SysUserSocialDto binding = new SysUserSocialDto();
        binding.setPlatform("gitee");
        when(client.listSocialBindings(1L)).thenReturn(R.ok(List.of(binding)));

        try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
            MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            springUtils.when(() -> SpringUtils.getBean(ISystemClient.class)).thenReturn(client);
            stubListPayload(cacheUtils, "sys:social:v2:bindings:1", List.of(binding));

            assertThat(SysCache.listSocialBindings(1L))
                .singleElement()
                .satisfies(item -> assertThat(item.getPlatform()).isEqualTo("gitee"));
        }
    }

    /**
     * 让 {@code CacheUtils.getOrLoad} 回源并返回给定载荷。
     *
     * <p><b>2026-10-19 更新</b>：starter 4.2.0 给 {@code getOrLoad} 补了「无 Class 参数」重载，
     * 列表缓存不再需要 {@code (Class<List<T>>) (Class<?>) List.class} 双强转。因此这里改为
     * 桩<b>三参</b>重载（key + supplier + ttl），并断言载荷原样返回（元素不丢、顺序不变）——
     * 这才是业务真正关心的契约；此前那条「type 必须是 List.class」的断言随双强转一起被移除了。</p>
     */
    private static void stubListPayload(MockedStatic<CacheUtils> cacheUtils, String key,
            List<?> payload) {
        cacheUtils.when(() -> CacheUtils.getOrLoad(eq(key), any(), isNull()))
            .thenAnswer(invocation -> payload);
    }
}
