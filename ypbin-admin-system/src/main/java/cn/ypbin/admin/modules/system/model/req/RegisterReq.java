/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.req;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户自助注册请求（认证入口）。
 *
 * <p>账号字段复用 {@link UserRegisterReq}，保证「入口收到的」与「落库用的」是同一份字段契约
 * （含长度与格式校验），不在此另抄一份；本类只追加认证入口特有的行为验证码字段。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
@Getter
@Setter
public class RegisterReq extends UserRegisterReq {

    /** 行为验证码 id（LOGIN_CAPTCHA_ENABLED 开启时必传） */
    private String captchaId;

    /** 行为验证码拖动轨迹（LOGIN_CAPTCHA_ENABLED 开启时必传） */
    private ImageCaptchaTrack captchaTrack;

    /** 归属验证码（REGISTER_VERIFY_MODE=SMS 时为手机验证码，=EMAIL 时为邮箱验证码） */
    @Size(max = 32, message = "验证码长度不能超过 32 个字符")
    private String verifyCode;
}
