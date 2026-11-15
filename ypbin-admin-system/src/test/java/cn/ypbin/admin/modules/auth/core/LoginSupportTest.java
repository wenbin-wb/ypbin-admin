/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.core;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.modules.auth.config.LoginClientRouteProperties.Route;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link LoginSupport} 的客户端解析单元测试。
 *
 * <p>客户端身份由服务端按登录入口绑定，不采信客户端自报：这里钉住「路径 → 客户端」的匹配与
 * 回落规则。密钥仍来自请求头，只对配置了密钥的客户端生效。</p>
 *
 * @author wenbin
 * @since 2026-11-14
 */
class LoginSupportTest {

    private List<Route> routes() {
        return List.of(route("/auth/miniapp/login", "miniapp"),
            route("/auth/social/callback/*", "web-admin"));
    }

    private Route route(String path, String clientId) {
        Route route = new Route();
        route.setPath(path);
        route.setClientId(clientId);
        return route;
    }

    @Test
    @DisplayName("请求路径命中入口：使用绑定的客户端，忽略客户端自报的任何标识")
    void shouldResolveClientByRequestPath() {
        assertThat(LoginSupport.resolveClientId("/auth/miniapp/login", routes(), "web-admin"))
            .isEqualTo("miniapp");
    }

    @Test
    @DisplayName("支持 Ant 通配：第三方回调按通配规则归到后台客户端")
    void shouldMatchWildcardRoute() {
        assertThat(LoginSupport.resolveClientId("/auth/social/callback/github", routes(), "web-admin"))
            .isEqualTo("web-admin");
    }

    @Test
    @DisplayName("入口未登记或无 Web 上下文：回落到配置的默认客户端")
    void shouldFallbackToDefaultClient() {
        assertThat(LoginSupport.resolveClientId("/auth/login", routes(), "web-admin"))
            .isEqualTo("web-admin");
        assertThat(LoginSupport.resolveClientId(null, routes(), "web-admin"))
            .isEqualTo("web-admin");
    }

    @Test
    @DisplayName("路径归一化：矩阵参数与百分号编码按 MVC 口径处理，避免被伪造路径挤到默认客户端")
    void shouldNormalizeRequestPathLikeMvc() {
        MockHttpServletRequest withMatrix = new MockHttpServletRequest();
        withMatrix.setRequestURI("/auth/miniapp/login;x=1");
        assertThat(LoginSupport.requestPath(withMatrix)).isEqualTo("/auth/miniapp/login");

        MockHttpServletRequest encoded = new MockHttpServletRequest();
        encoded.setRequestURI("/auth/miniapp/%6Cogin");
        assertThat(LoginSupport.requestPath(encoded)).isEqualTo("/auth/miniapp/login");

        assertThat(LoginSupport.requestPath(null)).isNull();
    }

    @Test
    @DisplayName("客户端密钥：配置了密钥的端带头才生效，未配置的端不带头")
    void shouldResolveClientSecret() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminConstants.CLIENT_SECRET_HEADER, "  s3cret  ");
        assertThat(LoginSupport.resolveClientSecret(request)).isEqualTo("s3cret");

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader(AdminConstants.CLIENT_SECRET_HEADER, "   ");
        assertThat(LoginSupport.resolveClientSecret(blank)).isNull();
        assertThat(LoginSupport.resolveClientSecret(null)).isNull();
    }
}
