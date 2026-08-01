/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.mapper.SysUserSocialMapper;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.social.core.SocialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第三方登录服务：code 换用户信息 → 查绑定 → 登录（或绑定已有账号/解绑）。
 *
 * <p>{@code SocialService} 仅在配置了至少一个第三方平台时由 starter 装配，故用 {@link ObjectProvider}
 * 可选注入；未配置任何平台时调用相关接口返回清晰业务错误，而非启动失败。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final ObjectProvider<SocialService> socialServiceProvider;
    private final SysUserService userService;
    private final SysUserSocialMapper socialMapper;
    private final SysPermissionService permissionService;

    private SocialService socialService() {
        SocialService service = socialServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException("未配置任何第三方登录平台");
        }
        return service;
    }

    /**
     * 用授权码完成第三方登录。已绑定的直接登录；未绑定的自动创建账号并绑定。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResp login(String source, String code, String state) {
        AuthUser authUser = socialService().login(source, buildCallback(code, state));

        SysUserSocial binding = socialMapper.selectOne(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getPlatform, source)
            .eq(SysUserSocial::getOpenId, authUser.getUuid()));
        if (binding != null) {
            // 已绑定：直接登录
            SysUser user = userService.getById(binding.getUserId());
            if (user == null) {
                throw new BusinessException("第三方账号关联的用户不存在");
            }
            doLogin(user);
            return new LoginResp(LoginHelper.getTokenValue());
        }
        // 未绑定：自动注册用户并绑定
        SysUser user = new SysUser();
        user.setUsername(source + "_" + authUser.getUuid());
        user.setRealName(authUser.getNickname());
        user.setNickname(authUser.getNickname());
        user.setAvatar(authUser.getAvatar());
        user.setPwdResetTime(java.time.LocalDateTime.now());
        userService.save(user);

        SysUserSocial social = new SysUserSocial();
        social.setUserId(user.getId());
        social.setPlatform(source);
        social.setOpenId(authUser.getUuid());
        social.setNickname(authUser.getNickname());
        social.setAvatar(authUser.getAvatar());
        social.setAccessToken(authUser.getToken().getAccessToken());
        socialMapper.insert(social);

        doLogin(user);
        return new LoginResp(LoginHelper.getTokenValue());
    }

    /**
     * 已登录用户绑定第三方账号。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bind(String source, String code, String state) {
        AuthUser authUser = socialService().login(source, buildCallback(code, state));
        Long userId = LoginHelper.getUserId();

        boolean exists = socialMapper.exists(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, userId)
            .eq(SysUserSocial::getPlatform, source));
        if (exists) {
            throw new BusinessException("该平台已绑定");
        }

        SysUserSocial social = new SysUserSocial();
        social.setUserId(userId);
        social.setPlatform(source);
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
            .eq(SysUserSocial::getPlatform, source));
    }

    /**
     * 当前用户已绑定的平台列表。
     */
    public List<String> boundPlatforms() {
        return socialMapper.selectList(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, LoginHelper.getUserId()))
            .stream().map(SysUserSocial::getPlatform).toList();
    }

    private AuthCallback buildCallback(String code, String state) {
        AuthCallback cb = new AuthCallback();
        cb.setCode(code);
        cb.setState(state);
        return cb;
    }

    private void doLogin(SysUser user) {
        LoginHelper.login(user.getId(), AdminConstants.CLIENT_WEB_ADMIN, "SOCIAL");
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername());
        loginUser.setNickname(user.getRealName());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setRoles(new HashSet<>(permissionService.listRoleCodes(user.getId())));
        UserContext.setLoginUser(loginUser);
        userService.updateLastLoginTime(user.getId());
    }
}
