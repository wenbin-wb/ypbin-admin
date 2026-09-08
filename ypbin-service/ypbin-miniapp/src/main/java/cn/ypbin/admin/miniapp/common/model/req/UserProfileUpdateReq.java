/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.common.model.req;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序用户资料更新请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class UserProfileUpdateReq {

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 手机号（可选） */
    private String phone;
}
