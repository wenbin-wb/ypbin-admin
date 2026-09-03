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
/**
 * 登录客户端一次性凭据响应。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Getter
@Setter
@AllArgsConstructor
public class ClientCredentialResp {

    private String clientId;

    private String clientSecret;
}
