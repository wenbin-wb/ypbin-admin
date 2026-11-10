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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.auth.dto.RegisterCodeSendReq;
import cn.ypbin.admin.auth.dto.RegisterReq;
import cn.ypbin.admin.auth.support.AuthConfigReader;
import cn.ypbin.admin.auth.support.LoginConfigKeys;
import cn.ypbin.admin.common.constant.RegisterConfigKeys;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.captcha.core.CaptchaService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 自助注册入口单元测试。
 *
 * <p>钉死三条对外契约：① 开关关闭时不产生任何远程调用；② system 的业务失败文案必须原样透传
 * （不能吞成通用「注册失败」）；③ 注册成功即建立登录态，响应与登录接口同构。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
class AuthServiceRegisterTest {

    private final ISystemClient systemClient = mock(ISystemClient.class);

    private final LoginSupport loginSupport = mock(LoginSupport.class);

    private final AuthConfigReader configReader = mock(AuthConfigReader.class);

    private final CaptchaService captchaService = mock(CaptchaService.class);

    private final SmsCodeService smsCodeService = mock(SmsCodeService.class);

    private final EmailCodeService emailCodeService = mock(EmailCodeService.class);

    private AuthService service() {
        return new AuthService(systemClient, loginSupport, configReader, captchaService,
            mock(PasswordAttemptLimiter.class), mock(LoginEventTracker.class),
            smsCodeService, emailCodeService);
    }

    private RegisterReq request() {
        RegisterReq req = new RegisterReq();
        req.setUsername("alice");
        req.setPassword("Passw0rd!");
        req.setRealName("爱丽丝");
        return req;
    }

    @Test
    @DisplayName("注册开关关闭：直接拒绝，不得调用 system")
    void shouldRejectWhenRegisterDisabled() {
        when(configReader.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)).thenReturn(false);

        assertThatThrownBy(() -> service().register(request(), "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("注册功能未开启");
        verify(systemClient, never()).registerUser(any());
    }

    @Test
    @DisplayName("system 业务失败（如用户名已存在）：原样透出远程提示")
    void shouldPropagateRemoteBusinessMessage() {
        when(configReader.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)).thenReturn(true);
        when(systemClient.registerUser(any())).thenReturn(R.fail("用户名已存在：alice"));

        assertThatThrownBy(() -> service().register(request(), "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("用户名已存在：alice");
        verify(loginSupport, never()).completeLogin(any(), any(), any(), any());
    }

    @Test
    @DisplayName("注册成功：自动登录并返回令牌")
    void shouldLoginAfterRegister() {
        SysUserDto user = new SysUserDto();
        user.setId(99L);
        user.setUsername("alice");
        when(configReader.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)).thenReturn(true);
        when(systemClient.registerUser(any())).thenReturn(R.ok(user));
        when(loginSupport.completeLogin(user, "ACCOUNT", "10.0.0.8", null))
            .thenReturn(new LoginResp("token-1", false));

        LoginResp resp = service().register(request(), "10.0.0.8", null);

        assertThat(resp.getAccessToken()).isEqualTo("token-1");
        verify(loginSupport).completeLogin(user, "ACCOUNT", "10.0.0.8", null);
    }

    @Test
    @DisplayName("验证码开关开启：缺验证码直接拒绝（登录/注册共用同一开关）")
    void shouldRequireCaptchaWhenEnabled() {
        when(configReader.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)).thenReturn(true);
        when(configReader.getBoolean(LoginConfigKeys.LOGIN_CAPTCHA_ENABLED, false)).thenReturn(true);

        assertThatThrownBy(() -> service().register(request(), "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("请先完成行为验证码");
        verify(systemClient, never()).registerUser(any());
    }

    @Test
    @DisplayName("短信验证模式：校验手机验证码后才调用 system")
    void shouldVerifySmsCodeWhenModeSms() {
        stubEnabled();
        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("SMS");
        SysUserDto created = new SysUserDto();
        created.setId(99L);
        when(systemClient.registerUser(any())).thenReturn(R.ok(created));
        RegisterReq req = request();
        req.setPhone("13800000000");
        req.setVerifyCode("123456");

        service().register(req, "10.0.0.8", null);

        verify(smsCodeService).verify("13800000000", "123456");
        verify(systemClient).registerUser(req);
    }

    @Test
    @DisplayName("短信验证模式：缺手机号/验证码直接拒绝，不调用 system 与短信服务")
    void shouldRejectSmsModeWithoutPhoneOrCode() {
        stubEnabled();
        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("SMS");

        assertThatThrownBy(() -> service().register(request(), "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("请输入验证码");
        verify(smsCodeService, never()).verify(any(), any());
        verify(systemClient, never()).registerUser(any());
    }

    @Test
    @DisplayName("邮箱验证模式：校验邮箱验证码后才调用 system")
    void shouldVerifyEmailCodeWhenModeEmail() {
        stubEnabled();
        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("EMAIL");
        SysUserDto created = new SysUserDto();
        created.setId(99L);
        when(systemClient.registerUser(any())).thenReturn(R.ok(created));
        RegisterReq req = request();
        req.setEmail("alice@example.com");
        req.setVerifyCode("654321");

        service().register(req, "10.0.0.8", null);

        verify(emailCodeService).verify("alice@example.com", "654321");
        verify(systemClient).registerUser(req);
    }

    @Test
    @DisplayName("发送注册验证码：渠道由配置决定，NONE 模式拒绝发送")
    void shouldSendRegisterCodeByConfiguredChannel() {
        stubEnabled();
        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("SMS");
        RegisterCodeSendReq smsReq = new RegisterCodeSendReq();
        smsReq.setPhone("13800000000");

        service().sendRegisterCode(smsReq);
        verify(smsCodeService).sendCode("13800000000");

        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("NONE");
        assertThatThrownBy(() -> service().sendRegisterCode(smsReq))
            .isInstanceOf(BusinessException.class)
            .hasMessage("当前注册方式无需验证码");
        verifyNoInteractions(emailCodeService);
    }

    @Test
    @DisplayName("短信验证模式：只给邮箱不给手机号 ⇒ 拒绝，不能换渠道绕过")
    void shouldRejectSmsModeWithOnlyEmail() {
        stubEnabled();
        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("SMS");
        RegisterReq req = request();
        req.setEmail("alice@example.com");
        req.setVerifyCode("123456");

        assertThatThrownBy(() -> service().register(req, "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("短信验证模式下手机号不能为空");
        verifyNoInteractions(smsCodeService, emailCodeService);
        verify(systemClient, never()).registerUser(any());
    }

    @Test
    @DisplayName("邮箱验证模式：只给手机号不给邮箱 ⇒ 拒绝")
    void shouldRejectEmailModeWithOnlyPhone() {
        stubEnabled();
        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("EMAIL");
        RegisterReq req = request();
        req.setPhone("13800000000");
        req.setVerifyCode("123456");

        assertThatThrownBy(() -> service().register(req, "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("邮箱验证模式下邮箱不能为空");
        verifyNoInteractions(smsCodeService, emailCodeService);
        verify(systemClient, never()).registerUser(any());
    }

    @Test
    @DisplayName("REGISTER_VERIFY_MODE 取值非法 ⇒ 显式失败，不静默降级为 NONE")
    void shouldFailClosedOnInvalidVerifyMode() {
        stubEnabled();
        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("MAIL");

        assertThatThrownBy(() -> service().register(request(), "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REGISTER_VERIFY_MODE 取值非法");
        verify(systemClient, never()).registerUser(any());
    }

    @Test
    @DisplayName("REGISTER_VERIFY_MODE 被清空 ⇒ 显式失败（清空不等于 NONE）")
    void shouldFailClosedOnBlankVerifyMode() {
        stubEnabled();
        when(configReader.getString(RegisterConfigKeys.REGISTER_VERIFY_MODE, "NONE"))
            .thenReturn("  ");

        assertThatThrownBy(() -> service().register(request(), "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("REGISTER_VERIFY_MODE 不能为空");
        verify(systemClient, never()).registerUser(any());
    }

    private void stubEnabled() {
        when(configReader.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)).thenReturn(true);
        when(configReader.getBoolean(LoginConfigKeys.LOGIN_CAPTCHA_ENABLED, false)).thenReturn(false);
    }

    @Test
    @DisplayName("system 不可达（降级返回失败 R）：给出服务不可用提示，不当作注册成功")
    void shouldFailWhenSystemUnavailable() {
        when(configReader.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)).thenReturn(true);
        when(systemClient.registerUser(any()))
            .thenReturn(R.fail(500, "系统服务暂不可用，请稍后重试"));

        assertThatThrownBy(() -> service().register(request(), "10.0.0.8", null))
            .isInstanceOf(BusinessException.class)
            .hasMessage("系统服务暂不可用，请稍后重试");
        verify(loginSupport, never()).completeLogin(any(), any(), eq("10.0.0.8"), any());
    }
}
