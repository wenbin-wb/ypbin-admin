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

import cn.ypbin.admin.auth.dto.PhoneLoginReq;
import cn.ypbin.admin.auth.support.AuthConfigReader;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.enums.UserStatusEnum;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter;
import java.util.Objects;
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
public class PhoneLoginStrategy {

    /** 短信验证码登录开关参数键 */
    private static final String KEY_LOGIN_SMS_ENABLED = "LOGIN_SMS_ENABLED";

    private final SmsCodeService smsCodeService;
    private final AuthConfigReader configReader;
    private final PasswordAttemptLimiter attemptLimiter;
    private final LoginSupport loginSupport;

    /**
     * 认证类型标识。
     *
     * @return 认证类型
     */
    public String authType() {
        return "PHONE";
    }

    /**
     * 执行手机验证码登录。
     *
     * @param req      登录请求
     * @param clientIp 客户端 IP
     * @return 登录结果
     */
    public LoginResp login(PhoneLoginReq req, String clientIp) {
        // 开关检查
        if (!configReader.getBoolean(KEY_LOGIN_SMS_ENABLED, false)) {
            throw new BusinessException("短信验证码登录未开启");
        }
        String phone = req.getPhone().trim();
        attemptLimiter.checkLocked(phone, clientIp);
        try {
            smsCodeService.verify(phone, req.getCode());
        } catch (BusinessException e) {
            attemptLimiter.recordFailure(phone, clientIp);
            throw e;
        }

        SysUser user = smsCodeService.getUserByPhone(phone);
        if (user == null || !Objects.equals(req.getTenantId(), user.getTenantId())) {
            attemptLimiter.recordFailure(phone, clientIp);
            throw new BusinessException("手机号未注册");
        }
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        attemptLimiter.reset(phone, clientIp);

        return loginSupport.completeLogin(user, authType());
    }
}
