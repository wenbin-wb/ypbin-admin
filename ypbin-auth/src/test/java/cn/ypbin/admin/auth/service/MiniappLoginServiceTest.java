/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.auth.config.WechatMiniappProperties;
import cn.ypbin.admin.auth.dto.MiniappLoginReq;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link MiniappLoginService} 单元测试。
 *
 * <p><b>为什么必须先测这里</b>：openid 是小程序侧全部接口的身份根，而历史实现有一条
 * 「未配置 Secret 就返回 {@code mock_<code.hashCode()>}」的静默兜底——同一 code 恒等同一账号，
 * 等于任何人可用任意 code 登录成他人。本测试把该行为钉死为「失败必须显式抛出」。</p>
 *
 * <p>不发起真实外网请求：{@code callWechatSession} 由子类替换。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class MiniappLoginServiceTest {

    private ISystemClient systemClient;
    private LoginSupport loginSupport;
    private WechatMiniappProperties properties;
    private MiniappLoginService service;

    @BeforeEach
    void setUp() {
        systemClient = mock(ISystemClient.class);
        loginSupport = mock(LoginSupport.class);
        properties = new WechatMiniappProperties();
        service = new MiniappLoginService(systemClient, loginSupport, new ObjectMapper(), properties);
    }

    private MiniappLoginReq req(String code) {
        MiniappLoginReq req = new MiniappLoginReq();
        req.setCode(code);
        return req;
    }

    /** 可替换微信响应的测试替身：不触碰网络 */
    private MiniappLoginService serviceWithWechatResponse(String response) {
        MiniappLoginService stub = new MiniappLoginService(systemClient, loginSupport,
            new ObjectMapper(), properties) {
            @Override
            String callWechatSession(String code) {
                return response;
            }
        };
        return stub;
    }

    private void mockUserCreated() {
        SysUserDto dto = new SysUserDto();
        dto.setId(99L);
        dto.setUsername("wx_openid-abc");
        when(systemClient.getOrCreateUserByUsername(anyString(), any(), any(), anyString(), anyString()))
            .thenReturn(R.ok(dto));
        when(loginSupport.completeLogin(any(), anyString(), any(), any()))
            .thenReturn(new LoginResp("token-xyz"));
    }

    @Test
    @DisplayName("未配置 appid/secret 且未开 Mock：拒绝登录，绝不退化为 mock 身份（防身份伪造）")
    void missingCredentialsShouldFailClosed() {
        // properties 全部为空 = 部署漏配
        assertThatThrownBy(() -> service.login(req("any-code"), "127.0.0.1", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未配置");
        // 关键反证：绝不能走到"建号/发令牌"，也不能把 code 变成身份
        verify(systemClient, never())
            .getOrCreateUserByUsername(anyString(), any(), any(), anyString(), anyString());
        verify(loginSupport, never()).completeLogin(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("code 为空：显式拒绝（不生成随机 Mock openid）")
    void blankCodeShouldThrow() {
        properties.setAppid("wx-app");
        properties.setSecret("wx-secret");
        assertThatThrownBy(() -> service.login(req("  "), "127.0.0.1", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("code");
    }

    @Test
    @DisplayName("请求 appid 与服务端配置不一致：拒绝登录（appid 由服务端说了算）")
    void mismatchedAppidShouldThrow() {
        properties.setAppid("wx-server-app");
        properties.setSecret("wx-secret");
        MiniappLoginReq req = req("code-1");
        req.setAppid("wx-attacker-app");
        assertThatThrownBy(() -> service.login(req, "127.0.0.1", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("appid");
    }

    @Test
    @DisplayName("凭据齐备且微信返回 openid：正常建号并签发令牌")
    void validCredentialsShouldLoginWithRealOpenid() {
        properties.setAppid("wx-app");
        properties.setSecret("wx-secret");
        mockUserCreated();
        MiniappLoginService stub = serviceWithWechatResponse("{\"openid\":\"openid-abc\"}");

        LoginResp resp = stub.login(req("good-code"), "127.0.0.1", "UA");

        assertThat(resp.getAccessToken()).isEqualTo("token-xyz");
        // username 必须由真实 openid 派生，不得是 mock_
        verify(systemClient).getOrCreateUserByUsername(
            eq("wx_openid-abc"), any(), any(), eq("MINIAPP"), anyString());
    }

    @Test
    @DisplayName("微信返回错误码（无 openid）：抛业务异常，不落 Mock 身份")
    void wechatErrorResponseShouldThrow() {
        properties.setAppid("wx-app");
        properties.setSecret("wx-secret");
        MiniappLoginService stub = serviceWithWechatResponse(
            "{\"errcode\":40029,\"errmsg\":\"invalid code\"}");

        assertThatThrownBy(() -> stub.login(req("bad-code"), "127.0.0.1", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("获取微信用户身份失败");
        verify(systemClient, never())
            .getOrCreateUserByUsername(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("微信响应非法 JSON：抛业务异常，不静默回退")
    void malformedWechatResponseShouldThrow() {
        properties.setAppid("wx-app");
        properties.setSecret("wx-secret");
        MiniappLoginService stub = serviceWithWechatResponse("{ 这不是合法 JSON");

        assertThatThrownBy(() -> stub.login(req("code-x"), "127.0.0.1", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("解析微信登录响应失败");
    }

    @Test
    @DisplayName("显式开启 Mock 时仍可本地登录（前缀 mock_），且该行为必须由配置显式打开")
    void mockModeOnlyWhenExplicitlyEnabled() {
        // 未开 Mock 的对照：同一 code 直接失败
        assertThatThrownBy(() -> service.login(req("dev-code"), "127.0.0.1", null))
            .isInstanceOf(BusinessException.class);

        properties.setMockEnabled(true);
        mockUserCreated();

        service.login(req("dev-code"), "127.0.0.1", null);

        // Mock 身份带可识别前缀，便于线上排查误开
        verify(systemClient).getOrCreateUserByUsername(
            org.mockito.ArgumentMatchers.startsWith("wx_mock_"), any(), any(), eq("MINIAPP"), anyString());
    }
}
