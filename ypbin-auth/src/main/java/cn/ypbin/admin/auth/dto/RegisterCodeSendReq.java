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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 注册验证码发送请求。
 *
 * <p>渠道由系统参数 {@code REGISTER_VERIFY_MODE} 决定，请求里只带目标地址：
 * {@code SMS} 用 {@link #phone}、{@code EMAIL} 用 {@link #email}。两个字段都可空，
 * 由服务端按当前模式校验「该渠道的地址必须存在」，避免客户端自行选择渠道绕过配置。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
@Getter
@Setter
public class RegisterCodeSendReq {

    /** 手机号（REGISTER_VERIFY_MODE=SMS 时必填） */
    @Size(max = 20, message = "手机号长度不能超过 20 个字符")
    private String phone;

    /** 邮箱（REGISTER_VERIFY_MODE=EMAIL 时必填） */
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    @Email(message = "邮箱格式不正确")
    private String email;
}
