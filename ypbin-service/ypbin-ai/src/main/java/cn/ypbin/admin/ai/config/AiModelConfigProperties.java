/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模型配置（{@code ypbin.ai.model-config.*}）。
 *
 * <p><strong>为什么要有本类（starter #103）</strong>：原先 {@code AiKeyCipher} 用 SpEL 风格的
 * 字段注入注解直接取这一个键。散落的字段注入既没有 IDE 补全、也不生成
 * {@code spring-configuration-metadata.json}，与「配置项统一 {@code PREFIX} 常量 + 禁散落字段注入」的
 * 规范冲突；绑定成 {@code @ConfigurationProperties} 后键名有唯一来源（{@link #PREFIX}），
 * 且缺配置的报错集中在 {@code AiKeyCipher} 的构造器校验里。</p>
 *
 * <p><strong>凭据卫生（红线）</strong>：{@link #secretKey} 是<b>真实凭据</b>（AES 密钥，用于加解密模型
 * API Key）。因此本类：</p>
 * <ul>
 *   <li><b>不覆写 {@code toString()}</b>——即刻意不接受 Lombok {@code @Data}/{@code @ToString}。
 *       Lombok 默认 {@code toString()} 会把字段值打出来，任何人误把它拼进日志/异常就会泄露密钥；</li>
 *   <li>字段只经构造器/绑定注入，不在本类里打日志、不放进异常消息（错误信息只描述「哪个键缺失/长度不对」，
 *       绝不带上值本身）。</li>
 * </ul>
 *
 * <p>本仓是业务仓：不覆写 {@code toString()} 属<b>有意的节制</b>，请勿为了「便于调试」补上。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@ConfigurationProperties(prefix = AiModelConfigProperties.PREFIX)
public class AiModelConfigProperties {

    /** 配置前缀（键名的唯一来源） */
    public static final String PREFIX = "ypbin.ai.model-config";

    /**
     * AI 模型 API Key 的加解密密钥（AES-GCM，长度必须为 16/24/32 字节）。
     *
     * <p><strong>真实凭据</strong>：由部署环境经环境变量 {@code AI_MODEL_SECRET_KEY} /
     * 配置中心注入，不得写入源码仓库或有默认值。</p>
     */
    private String secretKey;

    /**
     * 加解密密钥。
     *
     * @return 密钥明文（调用方不得记录到日志/异常）
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * 设置加解密密钥（由 Spring 绑定）。
     *
     * @param secretKey 密钥明文
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
