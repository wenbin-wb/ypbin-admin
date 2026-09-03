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

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * 手机验证码登录请求。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
public class PhoneLoginReq {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 短信验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;
}
