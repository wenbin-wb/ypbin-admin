/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.core;

import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.enums.UserStatusEnum;
import cn.ypbin.admin.modules.system.model.resp.LoginResp;
import cn.ypbin.admin.modules.auth.service.SmsCodeService;
import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.admin.modules.system.service.SysUserService;
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter;
import java.time.Duration;
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
    private final SysConfigService configService;
    private final PasswordAttemptLimiter attemptLimiter;
    private final LoginSupport loginSupport;
    private final BlocklistStore blocklistStore;
    private final BlocklistProperties blocklistProperties;

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
        String phone = phoneReq.getPhone().trim();
        attemptLimiter.checkLocked(phone, clientIp);
        try {
            smsCodeService.verify(phone, phoneReq.getCode());
        } catch (BusinessException e) {
            attemptLimiter.recordFailure(phone, clientIp);
            recordBlocklistViolation(clientIp);
            throw e;
        }

        SysUser user = userService.getByPhone(phone);
        if (user == null) {
            attemptLimiter.recordFailure(phone, clientIp);
            recordBlocklistViolation(clientIp);
            throw new BusinessException("手机号未注册");
        }
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        attemptLimiter.reset(phone, clientIp);

        return loginSupport.completeLogin(user, authType());
    }

    private void recordBlocklistViolation(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || !blocklistProperties.isEnabled()
            || !blocklistProperties.getAutoBan().isEnabled()) {
            return;
        }
        BlocklistProperties.AutoBan autoBan = blocklistProperties.getAutoBan();
        blocklistStore.recordViolation(clientIp, Duration.ofSeconds(autoBan.getWindowSeconds()),
            autoBan.getThreshold(), Duration.ofSeconds(autoBan.getBanSeconds()), "login failure");
    }
}
