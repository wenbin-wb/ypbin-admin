/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.common.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * 小程序用户信息响应。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class MiniappUserResp {

    /** 用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 微信 OpenID（去前缀后展示） */
    private String openid;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 头像末字提取 */
    private String avatarText;

    /** 手机号 */
    private String phone;
}
