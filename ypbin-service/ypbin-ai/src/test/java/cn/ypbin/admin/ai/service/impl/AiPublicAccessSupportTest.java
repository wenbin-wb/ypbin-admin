/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 公开访问密码失败计数测试。
 *
 * <p><b>背景</b>：失败计数此前只按令牌（分享链接本身）计数。令牌对所有访问者公开，
 * 任何一个拿到链接的人提交 5 次错误密码，就能把真正的访问者全部锁在
 * 「请 10 分钟后再试」之外——这是针对公开入口的拒绝服务。</p>
 *
 * <p>本测试锁定计数键包含「令牌 + 客户端 IP」：锁定只影响发起爆破的那个来源。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
class AiPublicAccessSupportTest {

    private final AiPublicAccessSupport support = new AiPublicAccessSupport(
        mockChatServiceProvider());

    @Test
    @DisplayName("密码错误：失败计数键 = 令牌 + 客户端 IP（不是仅令牌）")
    void shouldKeyFailureCounterByTokenAndClientIp() {
        try (MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            cacheUtils.when(() -> CacheUtils.get(anyString(), eq(Object.class))).thenReturn(null);
            cacheUtils.when(() -> CacheUtils.increment(anyString(), eq(1))).thenReturn(1L);

            assertThatThrownBy(() -> support.verifyPassword(
                "share-token", "203.0.113.7", sha256("secret"), "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("访问密码错误");

            cacheUtils.verify(() -> CacheUtils.increment(
                "ai:public:pwd:fail:share-token:203.0.113.7", 1));
        }
    }

    @Test
    @DisplayName("同一令牌不同 IP：各自独立的计数键（一个人爆破不会锁死其他人）")
    void shouldIsolateFailureCountersBetweenClientIps() {
        try (MockedStatic<CacheUtils> cacheUtils = mockStatic(CacheUtils.class)) {
            cacheUtils.when(() -> CacheUtils.get(anyString(), eq(Object.class))).thenReturn(null);
            cacheUtils.when(() -> CacheUtils.increment(anyString(), eq(1))).thenReturn(1L);

            assertThatThrownBy(() -> support.verifyPassword(
                "share-token", "203.0.113.7", sha256("secret"), "wrong"))
                .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> support.verifyPassword(
                "share-token", "198.51.100.9", sha256("secret"), "wrong"))
                .isInstanceOf(BusinessException.class);

            cacheUtils.verify(() -> CacheUtils.increment(
                "ai:public:pwd:fail:share-token:203.0.113.7", 1));
            cacheUtils.verify(() -> CacheUtils.increment(
                "ai:public:pwd:fail:share-token:198.51.100.9", 1));
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AiChatService> mockChatServiceProvider() {
        ObjectProvider<AiChatService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    /** 兼容历史分享链接的裸 SHA-256 哈希（BCrypt 需要 Spring 上下文，这里走历史格式）。 */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
