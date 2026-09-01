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

import lombok.Getter;
import lombok.Setter;
/**
 * 第三方登录平台配置响应。
 *
 * <p>共享类：复制自单体版 ypbin-admin-system，已归位至 api 模块，作为跨服务共享契约。
 * 不回传 ClientSecret 原文，仅以 {@code clientSecretConfigured} 标记是否已配置。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Getter
@Setter
public class SocialConfigResp {

    /** 平台标识（github/gitee/qq/wechat_open/alipay/dingtalk） */
    private String source;

    /** 是否启用 */
    private Boolean enabled;

    /** 应用 ClientId */
    private String clientId;

    /** 是否已配置 ClientSecret */
    private Boolean clientSecretConfigured;

    /** 授权回调地址 */
    private String redirectUri;

    /** 公钥（支付宝平台专用） */
    private String publicKey;
}
