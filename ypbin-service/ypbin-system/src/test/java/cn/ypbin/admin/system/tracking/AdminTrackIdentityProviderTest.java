/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link AdminTrackIdentityProvider} 单元测试。
 *
 * <p>身份上下文是静态 ThreadLocal，测试通过公开的 {@link IdentityContext#setLoginUser} 写入、
 * {@link IdentityContext#clear()} 清理；未登录（匿名采集）是主流场景，必须返回 {@code null} 而非抛异常。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class AdminTrackIdentityProviderTest {

    /** 测试用登录用户 ID。 */
    private static final long USER_ID = 42L;

    /** 测试用租户 ID。 */
    private static final long TENANT_ID = 1L;

    private final AdminTrackIdentityProvider provider = new AdminTrackIdentityProvider();

    @AfterEach
    void tearDown() {
        // 静态 ThreadLocal 必须显式清理，否则会串到同一线程上的其他用例
        IdentityContext.clear();
    }

    @Test
    void shouldResolveUserIdAndTenantIdFromLoginUser() {
        IdentityContext.setLoginUser(loginUser(USER_ID, TENANT_ID));

        assertThat(provider.userId()).isEqualTo(USER_ID);
        assertThat(provider.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void shouldReturnNullTenantWhenLoginUserHasNoTenant() {
        IdentityContext.setLoginUser(loginUser(USER_ID, null));

        assertThat(provider.userId()).isEqualTo(USER_ID);
        // 未指定租户与未登录是两件事，但都不构成错误，统一给 null
        assertThat(provider.tenantId()).isNull();
    }

    @Test
    void shouldReturnNullWhenAnonymous() {
        // 未写入任何身份：采集端点对匿名请求放行，这里不能抛异常
        assertThat(IdentityContext.isLogin()).isFalse();

        assertThat(provider.userId()).isNull();
        assertThat(provider.tenantId()).isNull();
    }

    @Test
    void shouldReturnNullUserIdWhenLoginUserHasNoId() {
        IdentityContext.setLoginUser(loginUser(null, TENANT_ID));

        assertThat(provider.userId()).isNull();
        assertThat(provider.tenantId()).isEqualTo(TENANT_ID);
    }

    private static LoginUser loginUser(Long id, Long tenantId) {
        LoginUser loginUser = new LoginUser(id, "tester");
        loginUser.setTenantId(tenantId);
        return loginUser;
    }
}
