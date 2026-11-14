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
import cn.ypbin.starter.security.client.LoginClientRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link LoginSupport} 的客户端解析单元测试。
 *
 * <p>客户端身份由请求头声明、策略由服务端 {@code sys_client} 决定：这里只钉住「头值如何被解析」，
 * 不枚举任何具体应用——新增应用不需要改后端代码。</p>
 *
 * @author wenbin
 * @since 2026-11-14
 */
class LoginSupportTest {

    @Test
    @DisplayName("请求头声明客户端：去掉首尾空白后使用声明值")
    void shouldUseDeclaredClientId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminConstants.CLIENT_ID_HEADER, "  miniapp  ");

        assertThat(LoginSupport.resolveClientId(request)).isEqualTo("miniapp");
    }

    @Test
    @DisplayName("未声明或空白：返回 null，由 starter 回落到配置的默认客户端")
    void shouldFallbackWhenHeaderMissing() {
        assertThat(LoginSupport.resolveClientId(new MockHttpServletRequest())).isNull();

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader(AdminConstants.CLIENT_ID_HEADER, "   ");
        assertThat(LoginSupport.resolveClientId(blank)).isNull();

        // 无 Web 上下文（定时任务等）同样按未声明处理
        assertThat(LoginSupport.resolveClientId(null)).isNull();
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
        // 客户端 ID 与密钥互不串用
        assertThat(LoginSupport.resolveClientId(request)).isNull();
    }

    @Test
    @DisplayName("组装客户端请求：ID、认证方式与密钥分别取自对应来源")
    void shouldBuildClientRequestFromHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminConstants.CLIENT_ID_HEADER, "miniapp");
        request.addHeader(AdminConstants.CLIENT_SECRET_HEADER, "s3cret");

        LoginClientRequest clientRequest = LoginSupport.buildClientRequest("MINIAPP", request);

        assertThat(clientRequest.getClientId()).isEqualTo("miniapp");
        assertThat(clientRequest.getAuthType()).isEqualTo("MINIAPP");
        assertThat(clientRequest.getClientSecret()).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("未声明客户端与密钥：两者都为空，由 starter 回落默认客户端且不做密钥校验")
    void shouldBuildEmptyClientRequestWithoutHeaders() {
        LoginClientRequest clientRequest = LoginSupport.buildClientRequest("ACCOUNT", null);

        assertThat(clientRequest.getClientId()).isNull();
        assertThat(clientRequest.getClientSecret()).isNull();
        assertThat(clientRequest.getAuthType()).isEqualTo("ACCOUNT");
    }
}
