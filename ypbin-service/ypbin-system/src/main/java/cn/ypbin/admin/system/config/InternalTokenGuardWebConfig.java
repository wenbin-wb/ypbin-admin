/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.config;

import cn.ypbin.admin.common.config.InternalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 内部端点凭证守卫装配。
 *
 * <p>将 {@link InternalTokenGuardInterceptor} 注册到 Web MVC 拦截链，且仅拦截
 * {@code /internal/**}；与 system-svc 本地 Sa-Token 拦截开关（{@code ypbin.security.interceptor}）
 * 相互独立，不影响其它路径的既有行为。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Configuration
@RequiredArgsConstructor
public class InternalTokenGuardWebConfig implements WebMvcConfigurer {

    private final InternalProperties internalProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalTokenGuardInterceptor(internalProperties))
            .addPathPatterns("/internal/**");
    }
}
