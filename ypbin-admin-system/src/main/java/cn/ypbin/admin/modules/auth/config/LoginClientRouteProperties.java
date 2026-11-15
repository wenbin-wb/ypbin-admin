/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录入口与客户端的绑定关系。
 *
 * <p>客户端身份由服务端按请求路径决定，而不是由客户端在请求头里自报：后者可以声称任意已登记的
 * 客户端，从而选择另一套 token 策略。新增终端只需新增登录入口并在 {@code routes} 里登记，
 * 不需要改 Java 代码；未命中的入口回落到 {@code ypbin.security.client.default-client-id}。</p>
 *
 * <p>用列表而不是 Map：入口路径包含 {@code /}，不是合法的 Spring 配置属性名，写成 Map 键会绑不上。</p>
 *
 * @author wenbin
 * @since 2026-11-15
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ypbin.admin.login-client")
public class LoginClientRouteProperties {

    /** 登录入口规则，按声明顺序匹配 */
    private List<Route> routes = new ArrayList<>();

    /**
     * 单条入口规则。
     */
    @Getter
    @Setter
    public static class Route {

        /** 登录入口 URI 模式（Ant 风格，如 {@code /auth/social/callback/*}） */
        private String path;

        /** 绑定的客户端 ID */
        private String clientId;
    }
}
