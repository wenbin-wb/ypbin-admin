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

import lombok.Data;

/**
 * 第三方登录平台配置响应。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Data
public class SocialConfigResp {

    private String source;

    private Boolean enabled;

    private String clientId;

    private Boolean clientSecretConfigured;

    private String redirectUri;

    private String publicKey;
}
