/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 第三方登录平台授权配置（内部 Feign 契约）。
 *
 * <p>含 ClientSecret 明文：仅限内部 Feign 端点（{@code /internal/**}）传递，供 auth-svc 构建
 * 授权请求；不对外暴露。对外查询接口请使用不含密钥的 {@code SocialConfigResp}。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Getter
@Setter
public class SocialAuthConfig {

    /** 平台标识（github/gitee/qq/wechat_open/alipay/dingtalk） */
    private String source;

    /** 是否启用 */
    private boolean enabled;

    /** 应用 ClientId */
    private String clientId;

    /** 应用 ClientSecret */
    private String clientSecret;

    /** 授权回调地址 */
    private String redirectUri;

    /** 公钥（支付宝平台专用） */
    private String publicKey;
}
