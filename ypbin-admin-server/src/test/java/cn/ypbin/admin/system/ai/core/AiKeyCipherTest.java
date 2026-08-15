/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link AiKeyCipher} 验证：默认开发密钥长度、加密/解密往返、非法密钥拒绝。
 *
 * @author wenbin
 * @since 2026-08-15
 */
class AiKeyCipherTest {

    @Test
    void devKeyIsValidAesLength() {
        // 未配置密钥时回退内置开发密钥，必须满足 AES 密钥长度要求（否则服务启动失败）
        AiKeyCipher cipher = new AiKeyCipher("");
        String encrypted = cipher.encrypt("sk-test-123456");
        assertThat(encrypted).isNotBlank();
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-test-123456");
    }

    @Test
    void encryptDecryptRoundTrip() {
        AiKeyCipher cipher = new AiKeyCipher("1234567890123456");
        String encrypted = cipher.encrypt("deepseek-api-key");
        assertThat(encrypted).isNotEqualTo("deepseek-api-key");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("deepseek-api-key");
    }

    @Test
    void blankValueReturnsNull() {
        AiKeyCipher cipher = new AiKeyCipher("1234567890123456");
        assertThat(cipher.encrypt("")).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void invalidKeyLengthRejected() {
        assertThatThrownBy(() -> new AiKeyCipher("short"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("16/24/32");
    }
}
