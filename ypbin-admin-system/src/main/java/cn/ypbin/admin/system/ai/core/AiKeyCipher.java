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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 模型 API Key 加密器（AES-GCM）。
 *
 * <p>密钥来自配置 {@code ypbin.ai.model-config.secret-key}（部署环境变量注入），
 * 未配置时使用内置开发密钥并告警，生产环境必须显式配置。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Component
public class AiKeyCipher {

    private static final Logger log = LoggerFactory.getLogger(AiKeyCipher.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 开发环境默认密钥（16 字节），生产必须通过 AI_MODEL_SECRET_KEY 覆盖 */
    private static final String DEV_KEY = "ypbin-ai-dev-key!";

    private final byte[] key;

    public AiKeyCipher(@Value("${ypbin.ai.model-config.secret-key:}") String secretKey) {
        String resolved = (secretKey == null || secretKey.isBlank()) ? DEV_KEY : secretKey;
        if (resolved.equals(DEV_KEY)) {
            log.warn("[ypbin-ai] 未配置 ypbin.ai.model-config.secret-key，API Key 使用内置开发密钥加密，生产环境必须通过环境变量 AI_MODEL_SECRET_KEY 注入");
        }
        byte[] raw = resolved.getBytes(StandardCharsets.UTF_8);
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException("ypbin.ai.model-config.secret-key 长度必须为 16/24/32 字节");
        }
        this.key = raw;
    }

    /** 加密明文，密文为 Base64(IV + cipherText) */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    /** 解密密文；解密失败时抛异常（不静默返回原文） */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败", e);
        }
    }
}
