/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import cn.ypbin.admin.modules.system.auth.LoginSupport;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.entity.SysUserSocial;
import cn.ypbin.admin.modules.system.mapper.SysUserSocialMapper;
import cn.ypbin.admin.modules.system.model.req.SocialCallbackReq;
import cn.ypbin.admin.modules.system.model.resp.LoginResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.social.core.SocialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 第三方登录服务：授权码换取用户信息并完成登录、绑定或解绑。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialService socialService;
    private final SysUserService userService;
    private final SysUserSocialMapper socialMapper;
    private final LoginSupport loginSupport;

    /**
     * 用授权码完成第三方登录。已绑定的直接登录；未绑定的自动创建账号并绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResp login(String source, SocialCallbackReq req) {
        String normalizedSource = normalizeSource(source);
        AuthUser authUser = socialService.login(normalizedSource, buildCallback(req));

        SysUserSocial binding = socialMapper.selectOne(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getPlatform, normalizedSource)
            .eq(SysUserSocial::getOpenId, authUser.getUuid()));
        if (binding != null) {
            // 已绑定：直接登录
            SysUser user = userService.getById(binding.getUserId());
            if (user == null) {
                throw new BusinessException("第三方账号关联的用户不存在");
            }
            return loginSupport.completeLogin(user, "SOCIAL");
        }
        throw new BusinessException("第三方账号尚未绑定，请先登录已有账号完成绑定");
    }

    /**
     * 已登录用户绑定第三方账号。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bind(String source, SocialCallbackReq req) {
        String normalizedSource = normalizeSource(source);
        AuthUser authUser = socialService.login(normalizedSource, buildCallback(req));
        Long userId = LoginHelper.getUserId();

        boolean userBound = socialMapper.exists(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, userId)
            .eq(SysUserSocial::getPlatform, normalizedSource));
        if (userBound) {
            throw new BusinessException("该平台已绑定");
        }
        boolean accountBound = socialMapper.exists(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getPlatform, normalizedSource)
            .eq(SysUserSocial::getOpenId, authUser.getUuid()));
        if (accountBound) {
            throw new BusinessException("该第三方账号已绑定其他用户");
        }

        SysUserSocial social = new SysUserSocial();
        social.setUserId(userId);
        social.setPlatform(normalizedSource);
        social.setOpenId(authUser.getUuid());
        social.setNickname(authUser.getNickname());
        social.setAvatar(authUser.getAvatar());
        social.setAccessToken(authUser.getToken().getAccessToken());
        socialMapper.insert(social);
    }

    /**
     * 解绑第三方账号。
     */
    public void unbind(String source) {
        Long userId = LoginHelper.getUserId();
        socialMapper.delete(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, userId)
            .eq(SysUserSocial::getPlatform, normalizeSource(source)));
    }

    /**
     * 当前用户已绑定的平台列表。
     */
    public List<String> boundPlatforms() {
        return socialMapper.selectList(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, LoginHelper.getUserId()))
            .stream().map(SysUserSocial::getPlatform).toList();
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
