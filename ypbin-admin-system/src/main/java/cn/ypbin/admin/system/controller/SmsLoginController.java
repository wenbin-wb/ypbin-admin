/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.controller;

import cn.ypbin.admin.system.auth.LoginStrategyFactory;
import cn.ypbin.admin.system.auth.PhoneLoginReq;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.service.SmsCodeService;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.tools.limiter.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手机验证码登录接口。开关由 LOGIN_SMS_ENABLED 控制。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequiredArgsConstructor
public class SmsLoginController extends BaseController {

    private final SmsCodeService smsCodeService;
    private final SysConfigService configService;
    private final LoginStrategyFactory loginStrategyFactory;

    /**
     * 发送验证码。每 IP 60 秒最多 5 次。
     */
    @PostMapping("/auth/sms/send")
    @RateLimit(count = 5, window = 60, message = "验证码发送过于频繁，请稍后再试")
    public R<Void> sendCode(@RequestParam String phone) {
        if (!configService.getBoolean("LOGIN_SMS_ENABLED", false)) {
            throw new BusinessException("短信验证码登录未开启");
        }
        smsCodeService.sendCode(phone);
        return ok();
    }

    /**
     * 手机验证码登录。经登录策略工厂派发到 PHONE 策略。
     */
    @Log(value = "手机验证码登录", module = "认证")
    @PostMapping("/auth/sms/login")
    public R<LoginResp> smsLogin(@Valid @RequestBody PhoneLoginReq req) {
        return ok(loginStrategyFactory.get("PHONE").login(req, ip()));
    }
}
