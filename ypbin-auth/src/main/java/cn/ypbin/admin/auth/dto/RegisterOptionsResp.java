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

import lombok.Getter;
import lombok.Setter;

/**
 * 注册入口的公开配置（供注册页决定展示哪些字段）。
 *
 * <p><b>为什么需要它</b>：验证渠道（无/短信/邮箱）与行为验证码开关都来自 {@code sys_config}，
 * 前端拿不到；若把三套字段都渲染出来，用户只能靠试错猜当前配置。本响应只暴露
 * 「是否开启、用哪个渠道、是否需要行为验证码」三项，不含任何敏感信息。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
@Getter
@Setter
public class RegisterOptionsResp {

    /** 注册总开关（REGISTER_ENABLED） */
    private boolean enabled;

    /** 归属验证方式：NONE / SMS / EMAIL（REGISTER_VERIFY_MODE，非法配置时接口直接失败） */
    private String verifyMode;

    /** 是否需要行为验证码（LOGIN_CAPTCHA_ENABLED） */
    private boolean captchaEnabled;
}
