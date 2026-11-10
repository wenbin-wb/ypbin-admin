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

import cn.ypbin.admin.auth.support.AuthConfigReader;
import cn.ypbin.admin.common.constant.RegisterConfigKeys;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.req.MailSendReq;
import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 邮箱注册验证码服务单元测试。
 *
 * <p>钉死四条：① 发码成功才写入缓存并重置失败计数；② 发信失败必须回滚验证码与冷却，
 * 且对外只给通用提示（不泄露 SMTP 细节）；③ 校验通过后验证码一次性消费；④ 邮箱大小写归一。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
class EmailCodeServiceTest {

    private static final String EMAIL = "alice@example.com";

    private static final String CODE_KEY = "email:register:code:" + EMAIL;

    private static final String COOLDOWN_KEY = "email:register:cooldown:" + EMAIL;

    private static final String FAIL_KEY = "email:register:verify:fail:" + EMAIL;

    private static final Pattern SIX_DIGITS = Pattern.compile("(\\d{6})");

    private final AuthConfigReader configReader = mock(AuthConfigReader.class);

    private final CacheService cacheService = mock(CacheService.class);

    private final ISystemClient systemClient = mock(ISystemClient.class);

    private final EmailCodeService service =
        new EmailCodeService(configReader, cacheService, systemClient);

    private void stubTtl() {
        when(configReader.getInt(RegisterConfigKeys.EMAIL_CODE_COOLDOWN_SECONDS, 60)).thenReturn(60);
        when(configReader.getInt(RegisterConfigKeys.EMAIL_CODE_EXPIRE_SECONDS, 300)).thenReturn(300);
    }

    @Test
    @DisplayName("发码成功：小写归一、写入缓存并投递邮件")
    void shouldStoreCodeAndSendMail() {
        stubTtl();
        when(cacheService.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(systemClient.sendMail(any())).thenReturn(R.ok());

        service.sendCode("Alice@Example.COM");

        ArgumentCaptor<MailSendReq> captor = ArgumentCaptor.forClass(MailSendReq.class);
        verify(systemClient).sendMail(captor.capture());
        assertThat(captor.getValue().getTo()).isEqualTo(EMAIL);
        Matcher matcher = SIX_DIGITS.matcher(captor.getValue().getContent());
        assertThat(matcher.find()).isTrue();
        String code = matcher.group(1);
        verify(cacheService).set(CODE_KEY, code, Duration.ofSeconds(300));
        verify(cacheService).delete(FAIL_KEY);
    }

    @Test
    @DisplayName("发信失败：回滚验证码与冷却，对外通用提示，不泄露 SMTP 细节")
    void shouldRollbackWhenMailFails() {
        stubTtl();
        when(cacheService.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(systemClient.sendMail(any()))
            .thenReturn(R.fail("smtp host 10.0.0.1 auth failed"));

        assertThatThrownBy(() -> service.sendCode(EMAIL))
            .isInstanceOf(BusinessException.class)
            .hasMessage("验证码邮件发送失败，请稍后重试");
        verify(cacheService).compareAndDelete(eq(CODE_KEY), anyString());
        verify(cacheService).compareAndDelete(eq(COOLDOWN_KEY), anyString());
        verify(cacheService, never()).delete(FAIL_KEY);
    }

    @Test
    @DisplayName("校验通过：验证码一次性消费并清除失败计数")
    void shouldConsumeCodeOnSuccess() {
        when(cacheService.get(FAIL_KEY, Object.class)).thenReturn(null);
        when(cacheService.compareAndDelete(CODE_KEY, "123456")).thenReturn(true);

        service.verify(EMAIL, "123456");

        verify(cacheService).delete(FAIL_KEY);
    }

    @Test
    @DisplayName("验证码错误：累加失败计数并给出可读提示")
    void shouldCountFailureOnWrongCode() {
        when(cacheService.get(FAIL_KEY, Object.class)).thenReturn(null);
        when(cacheService.compareAndDelete(CODE_KEY, "000000")).thenReturn(false);
        when(cacheService.exists(CODE_KEY)).thenReturn(true);
        when(cacheService.increment(FAIL_KEY, 1)).thenReturn(1L);

        assertThatThrownBy(() -> service.verify(EMAIL, "000000"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("验证码错误");
        verify(cacheService).expire(eq(FAIL_KEY), any(Duration.class));
    }
}
