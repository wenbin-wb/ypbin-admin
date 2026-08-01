/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResp {

    /** 访问令牌（Sa-Token token 值，前端加 Bearer 前缀后放入 Authorization 头） */
    private String accessToken;
}
