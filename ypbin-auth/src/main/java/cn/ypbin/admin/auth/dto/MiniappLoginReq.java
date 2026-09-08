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
 * 微信小程序登录请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class MiniappLoginReq {

    /** 微信小程序 AppID */
    private String appid;

    /** 微信登录凭证 code */
    private String code;

    /** 用户昵称（可选） */
    private String nickname;

    /** 用户头像链接（可选） */
    private String avatarUrl;
}
