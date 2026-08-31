/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.dto;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * 登录请求。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class LoginReq {

    /** 登录账号 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 行为验证码 id（LOGIN_CAPTCHA_ENABLED 开启时必传） */
    private String captchaId;

    /** 行为验证码拖动轨迹（LOGIN_CAPTCHA_ENABLED 开启时必传） */
    private ImageCaptchaTrack captchaTrack;
}
