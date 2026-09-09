/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.api.feign.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 内部端点调用凭证携带配置（system-svc Feign 客户端专用）。
 *
 * <p>system-svc 的 {@code /internal/**} 端点要求请求头 {@code X-Internal-Token} 与配置凭证一致，
 * 本配置为 {@link cn.ypbin.admin.system.api.feign.ISystemClient} 自动装配请求拦截器：每次调用
 * 从 Spring 环境读取 {@code ypbin.internal.token} 并写入该头。凭证未配置时记录告警并不携带凭证头
 * （服务端会拒绝），不静默崩溃。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Configuration
public class InternalTokenFeignConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFeignConfiguration.class);

    /** 内部调用凭证配置键（各服务经环境变量注入同一值） */
    public static final String TOKEN_PROPERTY = "ypbin.internal.token";

    /** 内部调用凭证请求头（与 system-svc 守卫一致） */
    public static final String TOKEN_HEADER = "X-Internal-Token";

    /**
     * 为 system-svc Feign 客户端注入携带内部凭证的请求拦截器。
     *
     * @param environment Spring 环境
     * @return 请求拦截器
     */
    @Bean
    public RequestInterceptor internalTokenRequestInterceptor(Environment environment) {
        return new RequestInterceptor() {

            private final AtomicBoolean warned = new AtomicBoolean(false);

            @Override
            public void apply(RequestTemplate template) {
                String token = environment.getProperty(TOKEN_PROPERTY, "");
                if (token == null || token.isBlank()) {
                    if (warned.compareAndSet(false, true)) {
                        log.warn("[system-api] 未配置内部调用凭证 {}（建议环境变量注入），"
                            + "system-svc /internal/** 调用将被拒绝", TOKEN_PROPERTY);
                    }
                    return;
                }
                template.header(TOKEN_HEADER, token);
            }
        };
    }
}
