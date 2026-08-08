/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.req;

import cn.ypbin.starter.log.annotation.LogMask;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

/**
 * 第三方登录平台配置更新请求。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Data
public class SocialConfigUpdateReq {

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    private String clientId;

    @LogMask
    @ToString.Exclude
    private String clientSecret;

    private String redirectUri;

    private String publicKey;
}
