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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户自助注册请求。
 *
 * <p><b>不含租户字段</b>：注册入口是匿名的，若允许调用方指定租户，任何人对任意租户都能自助开户。
 * 归属租户只由系统参数 {@code REGISTER_DEFAULT_TENANT_ID} 决定（服务端解析并校验租户可用）。</p>
 *
 * <p>字段长度与 {@code sys_user} 列定义一致，避免超长值先通过校验再由数据库报错。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
@Getter
@Setter
public class UserRegisterReq {

    /** 登录账号 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64 个字符")
    private String username;

    /** 登录密码（明文，落库前 BCrypt 加密） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 真实姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64 个字符")
    private String realName;

    /** 昵称（为空时取真实姓名） */
    @Size(max = 64, message = "昵称长度不能超过 64 个字符")
    private String nickname;

    /** 手机号（可空；填写时全局唯一） */
    @Size(max = 20, message = "手机号长度不能超过 20 个字符")
    private String phone;

    /** 邮箱（可空） */
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    @Email(message = "邮箱格式不正确")
    private String email;
}
