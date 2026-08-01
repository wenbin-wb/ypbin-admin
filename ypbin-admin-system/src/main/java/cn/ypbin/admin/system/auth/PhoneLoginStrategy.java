/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.auth;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.service.SmsCodeService;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 手机验证码登录策略。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
@RequiredArgsConstructor
public class PhoneLoginStrategy implements LoginStrategy {

    private final SmsCodeService smsCodeService;
    private final SysUserService userService;
    private final SysPermissionService permissionService;
    private final SysConfigService configService;
    private final PasswordAttemptLimiter attemptLimiter;

    @Override
    public String authType() {
        return "PHONE";
    }

    @Override
    public LoginResp login(Object req, String clientIp) {
        PhoneLoginReq phoneReq = (PhoneLoginReq) req;
        // 开关检查
        if (!configService.getBoolean("LOGIN_SMS_ENABLED", false)) {
            throw new BusinessException("短信验证码登录未开启");
        }
        // 校验验证码（消费一次性）
        smsCodeService.verify(phoneReq.getPhone(), phoneReq.getCode());

        // 锁定检查（按手机号维度）
        attemptLimiter.checkLocked(phoneReq.getPhone(), clientIp);

        SysUser user = userService.getByPhone(phoneReq.getPhone());
        if (user == null) {
            attemptLimiter.recordFailure(phoneReq.getPhone(), clientIp);
            throw new BusinessException("手机号未注册");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        attemptLimiter.reset(phoneReq.getPhone(), clientIp);

        LoginHelper.login(user.getId(), AdminConstants.CLIENT_WEB_ADMIN, AdminConstants.AUTH_TYPE_ACCOUNT);
        LoginUser loginUser = buildLoginUser(user);
        UserContext.setLoginUser(loginUser);
        userService.updateLastLoginTime(user.getId());

        return new LoginResp(LoginHelper.getTokenValue());
    }

    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername());
        loginUser.setNickname(user.getRealName());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setRoles(new HashSet<>(permissionService.listRoleCodes(user.getId())));
        return loginUser;
    }
}
