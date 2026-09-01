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

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.model.req.SocialCallbackReq;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.social.core.SocialService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 第三方登录服务：授权码换取用户信息并完成登录、绑定或解绑。
 *
 * <p>绑定数据存于 system-svc 共享库 sys_user_social（全局表，不隔离租户），本服务经
 * {@link ISystemClient} Feign 读写绑定记录，不直连共享库；用户信息同样经 Feign 获取。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialService socialService;
    private final ISystemClient systemClient;
    private final LoginSupport loginSupport;

    /**
     * 用授权码完成第三方登录。已绑定的直接登录；未绑定的抛出提示引导先绑定。
     */
    public LoginResp login(String source, SocialCallbackReq req) {
        String normalizedSource = normalizeSource(source);
        AuthUser authUser = socialService.login(normalizedSource, buildCallback(req));

        SysUserSocial binding = SysCache.getSocialBinding(normalizedSource, authUser.getUuid());
        if (binding == null) {
            throw new BusinessException("第三方账号尚未绑定，请先登录已有账号完成绑定");
        }
        SysUser user = fetchUser(binding.getUserId());
        return loginSupport.completeLogin(user, "SOCIAL");
    }

    /**
     * 已登录用户绑定第三方账号。
     */
    public void bind(String source, SocialCallbackReq req) {
        String normalizedSource = normalizeSource(source);
        AuthUser authUser = socialService.login(normalizedSource, buildCallback(req));
        Long userId = currentUserId();

        boolean userBound = SysCache.isSocialUserBound(userId, normalizedSource);
        if (userBound) {
            throw new BusinessException("该平台已绑定");
        }
        boolean accountBound = SysCache.isSocialAccountBound(normalizedSource, authUser.getUuid());
        if (accountBound) {
            throw new BusinessException("该第三方账号已绑定其他用户");
        }

        String accessToken = authUser.getToken() == null ? null : authUser.getToken().getAccessToken();
        systemClient.saveSocialBinding(userId, normalizedSource, authUser.getUuid(),
            authUser.getNickname(), authUser.getAvatar(), accessToken);
        SysCache.evictSocialBinding(userId, normalizedSource, authUser.getUuid());
    }

    /**
     * 解绑第三方账号。
     */
    public void unbind(String source) {
        Long userId = currentUserId();
        String normalizedSource = normalizeSource(source);
        systemClient.unbindSocial(userId, normalizedSource);
        SysCache.evictSocialBinding(userId, normalizedSource, null);
    }

    /**
     * 当前用户已绑定的平台列表。
     *
     * @return 平台标识列表，无绑定返回空集合
     */
    public List<String> boundPlatforms() {
        List<SysUserSocial> bindings = SysCache.listSocialBindings(currentUserId());
        if (bindings == null) {
            return List.of();
        }
        return bindings.stream().map(SysUserSocial::getPlatform).toList();
    }

    private SysUser fetchUser(Long userId) {
        SysUser user = SysCache.getUserById(userId);
        if (user == null) {
            throw new BusinessException("第三方账号关联的用户不存在");
        }
        return user;
    }

    private Long currentUserId() {
        return IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    private static String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            throw new BusinessException("第三方登录平台不能为空");
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    private AuthCallback buildCallback(SocialCallbackReq req) {
        if (!StringUtils.hasText(req.getCode()) && !StringUtils.hasText(req.getAuth_code())) {
            throw new BusinessException("第三方登录授权码不能为空");
        }
        AuthCallback callback = new AuthCallback();
        callback.setCode(req.getCode());
        callback.setAuth_code(req.getAuth_code());
        callback.setState(req.getState());
        return callback;
    }
}
