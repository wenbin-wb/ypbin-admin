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

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 第三方登录平台配置更新请求。
 *
 * <p>共享类：复制自单体版 ypbin-admin-system，已归位至 api 模块，作为跨服务共享契约。
 * ClientSecret 属敏感信息：配置接口的操作日志已整体排除请求体（{@code excludes}），
 * 接口响应与列表均只回传是否已配置（{@code clientSecretConfigured}），不回传原文。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Getter
@Setter
public class SocialConfigUpdateReq {

    /** 是否启用该平台 */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    /** 应用 ClientId */
    private String clientId;

    /** 应用 ClientSecret（留空表示不修改） */
    private String clientSecret;

    /** 授权回调地址 */
    private String redirectUri;

    /** 公钥（支付宝平台专用） */
    private String publicKey;
}
