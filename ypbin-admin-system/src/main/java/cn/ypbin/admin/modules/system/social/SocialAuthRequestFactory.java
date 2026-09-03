/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.social;

import cn.ypbin.starter.core.exception.BusinessException;
import java.util.List;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthAlipayRequest;
import me.zhyd.oauth.request.AuthDingTalkAccountRequest;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.request.AuthGithubRequest;
import me.zhyd.oauth.request.AuthQqRequest;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.request.AuthWeChatOpenRequest;
import org.springframework.stereotype.Component;

/**
 * 第三方登录授权请求工厂。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Component
public class SocialAuthRequestFactory {

    public static final List<String> SOURCES = List.of(
        "github", "gitee", "qq", "wechat_open", "alipay", "dingtalk");

    public AuthRequest create(String source, String clientId, String clientSecret,
                              String redirectUri, String publicKey) {
        AuthConfig config = AuthConfig.builder()
            .clientId(clientId)
            .clientSecret(clientSecret)
            .redirectUri(redirectUri)
            .build();
        return switch (source) {
            case "github" -> new AuthGithubRequest(config);
            case "gitee" -> new AuthGiteeRequest(config);
            case "qq" -> new AuthQqRequest(config);
            case "wechat_open" -> new AuthWeChatOpenRequest(config);
            case "alipay" -> new AuthAlipayRequest(config, publicKey);
            case "dingtalk" -> new AuthDingTalkAccountRequest(config);
            default -> throw new BusinessException("不支持的第三方登录平台：" + source);
        };
    }
}
