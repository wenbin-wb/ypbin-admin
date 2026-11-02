/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.config;

import cn.ypbin.admin.common.security.PwdChangeGuardInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册强制改密守卫（单体线，替代微服务网关的路径拦截）。
 *
 * @author wenbin
 * @since 2026-11-02
 */
@Configuration
@RequiredArgsConstructor
public class PwdChangeGuardConfig implements WebMvcConfigurer {

    private final PwdChangeGuardInterceptor pwdChangeGuardInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pwdChangeGuardInterceptor).addPathPatterns("/**");
    }
}
