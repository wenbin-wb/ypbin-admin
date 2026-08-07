/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.auth.LoginSupport;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.req.LoginReq;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.model.resp.UserInfoResp;
import cn.ypbin.admin.system.service.AuthService;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.captcha.core.CaptchaService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserService userService;
    private final SysPermissionService permissionService;
    private final SysMenuService menuService;
    private final PasswordAttemptLimiter attemptLimiter;
    private final LoginSupport loginSupport;
    private final CaptchaService captchaService;
    private final SysConfigService configService;

    @Override
    public LoginResp login(LoginReq req, String ip) {
        // 0. 开启登录验证码时，先校验行为验证码再查账号，防止撞库
        if (configService.getBoolean("LOGIN_CAPTCHA_ENABLED", false)) {
            if (req.getCaptchaId() == null || req.getCaptchaTrack() == null) {
                throw new BusinessException("请先完成验证码校验");
            }
            if (!captchaService.verify(req.getCaptchaId(), req.getCaptchaTrack())) {
                throw new BusinessException("验证码校验失败");
            }
        }
        String username = req.getUsername();
        // 1. 登录前判断账号是否已被错误锁定（已锁定抛 AccountLockedException）
        attemptLimiter.checkLocked(username, ip);

        SysUser user = userService.getByUsername(username);
        // 2. 校验用户存在与密码
        if (user == null || !PasswordEncoderUtil.matches(req.getPassword(), user.getPassword())) {
            attemptLimiter.recordFailure(username, ip);
            throw new BusinessException("用户名或密码错误");
        }
        // 3. 校验账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        // 4. 登录成功：清除错误计数
        attemptLimiter.reset(username, ip);

        // 5. 统一登录收尾：写入上下文（含客户端信息）+ 记录登录时间 + 返回令牌
        return loginSupport.completeLogin(user, AdminConstants.AUTH_TYPE_ACCOUNT);
    }

    @Override
    public void logout() {
        LoginHelper.logout();
    }

    @Override
    public UserInfoResp currentUserInfo() {
        Long userId = LoginHelper.getUserId();
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserInfoResp resp = new UserInfoResp();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setAvatar(user.getAvatar());
        resp.setDesc(user.getRemark());
        resp.setHomePath(null);
        resp.setRoles(permissionService.listRoleCodes(userId));
        resp.setPermissions(permissionService.listPermissions(userId));
        return resp;
    }

    @Override
    public List<String> currentPermissions() {
        return permissionService.listPermissions(LoginHelper.getUserId());
    }

    @Override
    public List<RouteResp> currentRoutes() {
        return menuService.buildRoutes(LoginHelper.getUserId());
    }
}
