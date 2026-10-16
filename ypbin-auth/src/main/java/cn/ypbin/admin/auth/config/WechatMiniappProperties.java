/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信小程序登录配置。
 *
 * <p>AppID 与 Secret 一律由服务端配置提供，<b>不采信请求方传入的 appid</b>：Secret 是换取 openid 的
 * 唯一凭证，若允许请求方指定 appid，攻击者可拿自己的 appid 与平台 Secret 组合试探，且请求方可用
 * 任意 appid 影响换取结果。请求里的 appid 仅用于「与配置是否一致」的校验（见
 * {@code MiniappLoginService}）。</p>
 *
 * <p>凭据属密钥，部署时经环境变量注入（{@code WECHAT_MINIAPP_APPID}/{@code WECHAT_MINIAPP_SECRET}），
 * 不落库、不入仓。</p>
 *
 * <p>{@code mockEnabled} 默认 <b>false</b>：未配置凭据时登录直接失败（fail-closed），
 * 而不是退化成「任意 code 都能换出稳定 openid」的伪登录。仅本地开发可显式开启。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
@Getter
@Setter
@ConfigurationProperties(prefix = WechatMiniappProperties.PREFIX)
public class WechatMiniappProperties {

    public static final String PREFIX = "ypbin.wechat.miniapp";

    /** 小程序 AppID（服务端配置，不采信请求入参） */
    private String appid;

    /** 小程序 AppSecret（换取 openid 用，属密钥） */
    private String secret;

    /** 是否允许本地 Mock 登录：默认关闭；生产环境必须保持关闭 */
    private boolean mockEnabled;

    /**
     * 凭据是否齐备。
     *
     * @return appid 与 secret 均已配置时为 true
     */
    public boolean credentialsConfigured() {
        return appid != null && !appid.isBlank() && secret != null && !secret.isBlank();
    }
}
