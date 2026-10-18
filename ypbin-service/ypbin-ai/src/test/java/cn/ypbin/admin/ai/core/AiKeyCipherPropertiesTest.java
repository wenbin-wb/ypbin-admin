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

import cn.ypbin.admin.ai.config.AiModelConfigProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AiKeyCipher} 的配置强类型化与凭据卫生测试（对应 starter #103）。
 *
 * <p><b>修的是什么</b>：原先用 SpEL 风格的字段注入注解直接取这一个键，既没有 IDE 补全/配置元数据，
 * 键名也散落在注解字符串里。现改为 {@link AiModelConfigProperties} 构造器注入，
 * 键名唯一来源是 {@code AiModelConfigProperties.PREFIX}。</p>
 *
 * <p><b>本测试锁定三件事</b>：① 长度校验仍然生效（16/24/32 字节，其余拒绝启动）；
 * ② 缺失/为空仍拒绝启动（不允许退回内置默认密钥）；③ <b>凭据卫生</b>——异常消息与
 * {@code toString()} 都不得包含密钥值（这是 properties 类刻意不覆写 {@code toString()} 的原因，
 * 一旦有人为了调试补上 Lombok {@code @ToString}，第 ③ 条会转红）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class AiKeyCipherPropertiesTest {

    /** 恰好 16 字节的合法密钥（AES-128） */
    private static final String VALID_KEY = "0123456789abcdef";

    @Test
    @DisplayName("合法密钥（16/24/32 字节）可构造，加解密往返一致")
    void shouldAcceptValidKeyLengths() {
        for (int length : new int[] {16, 24, 32}) {
            String key = "k".repeat(length);
            AiKeyCipher cipher = new AiKeyCipher(properties(key));
            String encrypted = cipher.encrypt("plain-text");
            assertThat(encrypted).isNotEqualTo("plain-text");
            assertThat(cipher.decrypt(encrypted)).isEqualTo("plain-text");
        }
    }

    @Test
    @DisplayName("密钥缺失：拒绝启动，错误信息只说明「哪个键缺失」且不含任何密钥值")
    void shouldFailFastWhenSecretKeyMissing() {
        assertThatThrownBy(() -> new AiKeyCipher(properties(null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(AiModelConfigProperties.PREFIX + ".secret-key")
            .hasMessageContaining("缺失");

        assertThatThrownBy(() -> new AiKeyCipher(properties("   ")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("缺失");
    }

    @Test
    @DisplayName("密钥长度非法：拒绝启动，错误信息给出「期望/实际字节数」但**不含密钥值**")
    void shouldRejectIllegalKeyLengthWithoutLeakingSecret() {
        // 15 字节：非法
        String illegal = "x".repeat(15);
        assertThatThrownBy(() -> new AiKeyCipher(properties(illegal)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("16/24/32")
            .hasMessageContaining("15")
            .as("异常消息绝不能带出密钥值（否则失败日志即泄露凭据）")
            .hasMessageNotContaining(illegal);
    }

    @Test
    @DisplayName("凭据卫生：properties 类不得覆写 toString 输出密钥（防误打日志泄露）")
    void propertiesMustNotLeakSecretThroughToString() {
        AiModelConfigProperties properties = properties(VALID_KEY);
        // 未覆写 toString ⇒ 走 Object 默认实现（类名@hash），不可能带出字段值
        assertThat(properties.toString())
            .as("AiModelConfigProperties 一旦覆写 toString（如加 Lombok @ToString/@Data），"
                + "任何人误把它拼进日志都会泄露 AES 密钥——本断言把它挡在构建期")
            .doesNotContain(VALID_KEY);
    }

    @Test
    @DisplayName("PREFIX 常量是键名的唯一来源（避免注解里的键字符串与配置文件漂移）")
    void prefixConstantShouldBeSingleSourceOfTruth() {
        assertThat(AiModelConfigProperties.PREFIX).isEqualTo("ypbin.ai.model-config");
        assertThat(VALID_KEY.getBytes(StandardCharsets.UTF_8)).hasSize(16);
    }

    private static AiModelConfigProperties properties(String secretKey) {
        AiModelConfigProperties properties = new AiModelConfigProperties();
        properties.setSecretKey(secretKey);
        return properties;
    }
}
