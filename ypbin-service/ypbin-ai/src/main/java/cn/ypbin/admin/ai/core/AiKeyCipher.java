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

import cn.ypbin.admin.ai.config.AiModelConfigProperties;
import cn.ypbin.starter.tools.crypto.AesUtils;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * AI 模型 API Key 加密器（基于 starter 的 AES-GCM 工具）。
 *
 * <p>委托 {@link AesUtils} 完成 AES-GCM 认证加密（每次随机 12 字节 IV 前置密文），
 * 密钥必须通过配置 {@code ypbin.ai.model-config.secret-key}（部署环境变量
 * {@code AI_MODEL_SECRET_KEY} 注入）显式提供，未配置即启动失败，不允许使用内置默认密钥。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Component
public class AiKeyCipher {

    private final byte[] key;

    /**
     * 从强类型配置构造（starter #103：替换原先散落的字段注入注解）。
     *
     * <p>密钥来自 {@link AiModelConfigProperties#getSecretKey()}，键名唯一来源是该类的
     * {@code PREFIX} 常量；本构造器只做「缺失/长度」校验，<b>绝不把密钥值写进异常消息或日志</b>。</p>
     *
     * @param properties AI 模型配置（真实凭据，不得记录）
     */
    public AiKeyCipher(AiModelConfigProperties properties) {
        String secretKey = properties.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                "未配置 " + AiModelConfigProperties.PREFIX + ".secret-key（环境变量 AI_MODEL_SECRET_KEY），"
                    + "AI 模型 API Key 加密密钥缺失，拒绝启动");
        }
        byte[] raw = secretKey.getBytes(StandardCharsets.UTF_8);
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                AiModelConfigProperties.PREFIX + ".secret-key 长度必须为 16/24/32 字节，当前为 "
                    + raw.length + " 字节");
        }
        this.key = raw;
    }

    /** 加密明文，密文为 Base64(IV + cipherText)；空白输入返回 null */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        return AesUtils.encrypt(plainText, key);
    }

    /** 解密密文；解密失败时抛异常（不静默返回原文）；空白输入返回 null */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        return AesUtils.decrypt(cipherText, key);
    }
}
