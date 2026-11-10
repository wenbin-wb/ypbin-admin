/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.service.impl;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.common.constant.RegisterConfigKeys;
import cn.ypbin.admin.modules.auth.core.LoginSupport;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.enums.UserStatusEnum;
import cn.ypbin.admin.modules.system.model.req.LoginReq;
import cn.ypbin.admin.modules.system.model.req.RegisterReq;
import cn.ypbin.admin.modules.system.model.resp.LoginResp;
import cn.ypbin.admin.modules.system.model.resp.RouteResp;
import cn.ypbin.admin.modules.system.model.resp.UserInfoResp;
import cn.ypbin.admin.modules.auth.service.AuthService;
import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.admin.modules.system.service.SysMenuService;
import cn.ypbin.admin.modules.system.service.SysPermissionService;
import cn.ypbin.admin.modules.system.service.SysUserService;
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
            requireCaptchaPassed(req.getCaptchaId(), req.getCaptchaTrack());
        }
        String username = req.getUsername();
        // 1. 登录前判断账号是否已被错误锁定（已锁定抛 AccountLockedException）
        attemptLimiter.checkLocked(username, ip);

        SysUser user = userService.getByUsername(username);
        // 2. 校验用户存在与密码
        if (user == null || !PasswordEncoderUtil.matches(req.getPassword(), user.getPassword())) {
            // starter 的 PasswordAttemptLimiter 已把失败计入 IP 自动封禁，这里不要重复计数
            attemptLimiter.recordFailure(username, ip);
            throw new BusinessException("用户名或密码错误");
        }
        // 3. 校验账号状态
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        // 4. 登录成功：清除错误计数
        attemptLimiter.reset(username, ip);

        // 5. 统一登录收尾：写入上下文（含客户端信息）+ 记录登录时间 + 返回令牌
        return loginSupport.completeLogin(user, AdminConstants.AUTH_TYPE_ACCOUNT);
    }

    /**
     * 用户自助注册：校验开关与验证码后落库，并直接建立登录态。
     *
     * <p><b>落库语义由 {@code SysUserService#registerUser} 单点决定</b>：归属租户、默认角色、
     * 用户名/手机号查重与密码策略都只有 system 用户服务有数据源与唯一口径。本类只负责
     * 匿名入口的开关、验证码与登录收尾，避免同一规则在两层各写一份而漂移。</p>
     *
     * <p>失败提示原样抛给调用方：用户名重复等业务错误必须让用户看到具体原因，不能吞成通用失败。</p>
     */
    @Override
    public LoginResp register(RegisterReq req, String ip) {
        if (!configService.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)) {
            throw new BusinessException("注册功能未开启");
        }
        if (configService.getBoolean("LOGIN_CAPTCHA_ENABLED", false)) {
            requireCaptchaPassed(req.getCaptchaId(), req.getCaptchaTrack());
        }
        SysUser user = userService.registerUser(req);
        if (user == null) {
            throw new BusinessException("注册失败，请稍后重试");
        }
        return loginSupport.completeLogin(user, AdminConstants.AUTH_TYPE_ACCOUNT);
    }

    /**
     * 强制校验请求携带的行为验证码（登录/注册共用）。
     *
     * @param captchaId    验证码 id
     * @param captchaTrack 拖动轨迹
     */
    private void requireCaptchaPassed(String captchaId, ImageCaptchaTrack captchaTrack) {
        if (captchaId == null || captchaTrack == null) {
            throw new BusinessException("请先完成验证码校验");
        }
        if (!captchaService.verify(captchaId, captchaTrack)) {
            throw new BusinessException("验证码校验失败");
        }
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
