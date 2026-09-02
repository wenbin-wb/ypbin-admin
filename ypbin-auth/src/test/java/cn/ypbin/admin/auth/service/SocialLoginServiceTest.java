/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.model.req.SocialCallbackReq;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.social.core.SocialService;
import java.util.List;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link SocialLoginService} 单元测试。
 *
 * <p>验证第三方登录/绑定/解绑流程（绑定数据走 SysCache，写操作经 Feign + 清缓存）。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
class SocialLoginServiceTest {

    private SocialService socialService;
    private ISystemClient systemClient;
    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        socialService = org.mockito.Mockito.mock(SocialService.class);
        systemClient = org.mockito.Mockito.mock(ISystemClient.class);
        service = new SocialLoginService(socialService, systemClient,
            org.mockito.Mockito.mock(LoginSupport.class));
    }

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    private AuthUser authUser() {
        AuthUser user = new AuthUser();
        user.setUuid("openid-123");
        user.setNickname("github-user");
        return user;
    }

    private SysUserSocial binding() {
        SysUserSocial social = new SysUserSocial();
        social.setUserId(42L);
        social.setPlatform("github");
        social.setOpenId("openid-123");
        return social;
    }

    private SocialCallbackReq callbackReq() {
        SocialCallbackReq req = new SocialCallbackReq();
        req.setCode("auth-code");
        return req;
    }

    @Test
    void loginShouldThrowWhenNotBound() throws Exception {
        when(socialService.login(any(), any(AuthCallback.class))).thenReturn(authUser());

        try (MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {
            sysCache.when(() -> SysCache.getSocialBinding("github", "openid-123")).thenReturn(null);

            assertThatThrownBy(() -> service.login("github", callbackReq()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未绑定");
        }
    }

    @Test
    void bindShouldSaveAndEvictCache() throws Exception {
        when(socialService.login(any(), any(AuthCallback.class))).thenReturn(authUser());
        IdentityContext.setLoginUser(loginUser(42L));

        try (MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {
            sysCache.when(() -> SysCache.isSocialUserBound(42L, "github")).thenReturn(false);
            sysCache.when(() -> SysCache.isSocialAccountBound("github", "openid-123")).thenReturn(false);
            when(systemClient.saveSocialBinding(42L, "github", "openid-123", "github-user", null, null))
                .thenReturn(R.ok());

            service.bind("github", callbackReq());

            verify(systemClient).saveSocialBinding(42L, "github", "openid-123", "github-user", null, null);
            sysCache.verify(() -> SysCache.evictSocialBinding(42L, "github", "openid-123"));
        }
    }

    @Test
    void bindShouldThrowWhenPlatformAlreadyBound() throws Exception {
        when(socialService.login(any(), any(AuthCallback.class))).thenReturn(authUser());
        IdentityContext.setLoginUser(loginUser(42L));

        try (MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {
            sysCache.when(() -> SysCache.isSocialUserBound(42L, "github")).thenReturn(true);

            assertThatThrownBy(() -> service.bind("github", callbackReq()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该平台已绑定");
        }
    }

    @Test
    void boundPlatformsShouldReturnEmptyWhenNoBindings() {
        IdentityContext.setLoginUser(loginUser(42L));

        try (MockedStatic<SysCache> sysCache = mockStatic(SysCache.class)) {
            sysCache.when(() -> SysCache.listSocialBindings(42L)).thenReturn(List.of());

            List<String> platforms = service.boundPlatforms();

            assertThat(platforms).isEmpty();
        }
    }

    private cn.ypbin.starter.security.core.LoginUser loginUser(long id) {
        cn.ypbin.starter.security.core.LoginUser user = new cn.ypbin.starter.security.core.LoginUser();
        user.setId(id);
        return user;
    }
}
