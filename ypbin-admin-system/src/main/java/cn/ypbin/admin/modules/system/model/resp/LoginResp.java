/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.resp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * 登录响应。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResp {

    /** 访问令牌（Sa-Token token 值） */
    private String accessToken;

    /**
     * 是否必须先修改密码。
     *
     * <p>为 {@code true} 时前端应直接进入改密页；拦截器也会拦截除改密/登出等白名单外的请求。</p>
     */
    private Boolean mustChangePassword;
}
