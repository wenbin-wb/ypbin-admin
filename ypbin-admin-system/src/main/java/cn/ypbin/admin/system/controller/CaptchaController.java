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

import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.captcha.core.CaptchaService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码接口。行为验证码（滑块/旋转/点选/拼接），开关由系统参数 LOGIN_CAPTCHA_ENABLED 控制。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;
    private final SysConfigService configService;

    /**
     * 获取验证码。登录开关关闭时返回空。
     */
    @GetMapping
    public R<?> generate() {
        if (!configService.getBoolean("LOGIN_CAPTCHA_ENABLED", false)) {
            return R.ok();
        }
        ApiResponse<?> data = captchaService.generate();
        return R.ok(data);
    }

    /**
     * 校验验证码。
     */
    @PostMapping("/verify")
    public R<Boolean> verify(@RequestParam String id, @RequestBody ImageCaptchaTrack track) {
        boolean ok = captchaService.verify(id, track);
        if (!ok) {
            throw new BusinessException("验证码校验失败");
        }
        return R.ok(ok);
    }
}
