/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.social;

import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.social.core.AuthRequestProvider;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.request.AuthAlipayRequest;
import me.zhyd.oauth.request.AuthDingTalkAccountRequest;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.request.AuthGithubRequest;
import me.zhyd.oauth.request.AuthQqRequest;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.request.AuthWeChatOpenRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 第三方登录平台自动装配。从 sys_config 读取 clientId/secret/redirectUri，
 * 始终注册 Provider（未配的用空值，实际使用 JustAuth 报清晰错误）。键前缀 {@code SOCIAL_{PLATFORM}_}。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Configuration
public class SocialAuthConfiguration {

    private final SysConfigService config;

    public SocialAuthConfiguration(SysConfigService config) {
        this.config = config;
    }

    @Bean
    public AuthRequestProvider githubProvider() {
        AuthConfig cfg = buildConfig("GITHUB");
        return cfg == null ? null : provider("github", new AuthGithubRequest(cfg));
    }

    @Bean
    public AuthRequestProvider giteeProvider() {
        AuthConfig cfg = buildConfig("GITEE");
        return cfg == null ? null : provider("gitee", new AuthGiteeRequest(cfg));
    }

    @Bean
    public AuthRequestProvider qqProvider() {
        AuthConfig cfg = buildConfig("QQ");
        return cfg == null ? null : provider("qq", new AuthQqRequest(cfg));
    }

    @Bean
    public AuthRequestProvider wechatOpenProvider() {
        AuthConfig cfg = buildConfig("WECHAT_OPEN");
        return cfg == null ? null : provider("wechat_open", new AuthWeChatOpenRequest(cfg));
    }

    @Bean
    public AuthRequestProvider alipayProvider() {
        AuthConfig cfg = buildConfig("ALIPAY");
        return cfg == null ? null : provider("alipay", new AuthAlipayRequest(cfg));
    }

    @Bean
    public AuthRequestProvider dingtalkProvider() {
        AuthConfig cfg = buildConfig("DINGTALK");
        return cfg == null ? null : provider("dingtalk", new AuthDingTalkAccountRequest(cfg));
    }

    /**
     * 构建平台 OAuth 配置；clientId 未配置时返回 null（该平台不注册，避免 JustAuth 构造器校验失败拖垮启动）。
     */
    private AuthConfig buildConfig(String platform) {
        String prefix = "SOCIAL_" + platform + "_";
        String clientId = config.getString(prefix + "CLIENT_ID", "");
        if (clientId.isBlank()) {
            return null;
        }
        return AuthConfig.builder()
            .clientId(clientId)
            .clientSecret(config.getString(prefix + "CLIENT_SECRET", ""))
            .redirectUri(config.getString(prefix + "REDIRECT_URI", ""))
            .build();
    }

    private static AuthRequestProvider provider(String source, AuthRequest request) {
        return new AuthRequestProvider() {
            @Override public String getSource() { return source; }
            @Override public AuthRequest getAuthRequest() { return request; }
        };
    }
}
