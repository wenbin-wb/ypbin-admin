/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.api.feign.ISystemClientFallback;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ai 侧平台用户判定器测试：命中/未命中走 Feign，依赖故障必须显式失败而不是静默判为非平台用户。
 *
 * <p>本测试对应的是一个真实缺陷：starter 的 {@code PlatformUserChecker} 端口在宿主未实现时
 * fail-closed（恒为否），ai 有 {@code @PlatformAccess} 标注却一直没有判定器，
 * 于是 AI 模型配置等页面必然返回 403。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class AiPlatformUserCheckerTest {

    private final ISystemClient systemClient = mock(ISystemClient.class);

    private final AiPlatformUserChecker checker = new AiPlatformUserChecker(systemClient);

    @Test
    @DisplayName("命中：system 判定为平台用户时放行，且按 userId 精确调用一次")
    void shouldPassWhenSystemConfirmsPlatformUser() {
        when(systemClient.isPlatformUser(1001L)).thenReturn(R.ok(Boolean.TRUE));

        assertThat(checker.isPlatformUser(1001L)).isTrue();

        verify(systemClient).isPlatformUser(1001L);
        verifyNoMoreInteractions(systemClient);
    }

    @Test
    @DisplayName("未命中：system 判定为非平台用户时拒绝，且不得放行")
    void shouldRejectWhenSystemDeniesPlatformUser() {
        when(systemClient.isPlatformUser(2002L)).thenReturn(R.ok(Boolean.FALSE));

        assertThat(checker.isPlatformUser(2002L)).isFalse();

        verify(systemClient).isPlatformUser(2002L);
    }

    @Test
    @DisplayName("降级：Feign 降级实现返回失败 R 时必须上抛显式错误，绝不静默判为「非平台用户」")
    void shouldFailLoudlyWhenSystemUnavailable() {
        // 真实降级 Bean，而不是自造一个失败 R —— 这样降级语义改动会在本用例里露馅
        ISystemClientFallback fallback = new ISystemClientFallback();
        when(systemClient.isPlatformUser(3003L)).thenReturn(fallback.isPlatformUser(3003L));

        assertThatThrownBy(() -> checker.isPlatformUser(3003L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("平台用户判定失败")
            .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                .isEqualTo(GlobalErrorCode.INTERNAL_ERROR.getCode()));
    }

    @Test
    @DisplayName("异常契约：响应为 null（Feign 未接降级）时同样显式失败")
    void shouldFailLoudlyWhenResponseIsNull() {
        when(systemClient.isPlatformUser(4004L)).thenReturn(null);

        assertThatThrownBy(() -> checker.isPlatformUser(4004L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("平台用户判定失败");
    }

    @Test
    @DisplayName("成功响应但 data 缺失时判为故障（不当作非平台用户的正常结论）")
    void shouldFailLoudlyWhenDataMissing() {
        when(systemClient.isPlatformUser(5005L)).thenReturn(R.ok(null));

        assertThatThrownBy(() -> checker.isPlatformUser(5005L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("平台用户判定失败");
    }

    @Test
    @DisplayName("身份缺失：userId 为 null 时显式拒绝，且不发起 RPC")
    void shouldRejectNullUserIdWithoutRemoteCall() {
        assertThatThrownBy(() -> checker.isPlatformUser(null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("用户身份缺失");

        verifyNoMoreInteractions(systemClient);
    }
}
