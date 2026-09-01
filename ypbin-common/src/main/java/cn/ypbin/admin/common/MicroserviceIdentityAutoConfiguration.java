/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common;

import cn.ypbin.admin.common.IdentityHeaderFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 微服务下游身份头自动配置。
 *
 * <p>为业务服务装配 {@link IdentityHeaderFilter}：解析网关签发的内部身份头构建
 * {@code IdentityContext}。各微服务只需引入 common-api 即自动生效。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@AutoConfiguration
public class MicroserviceIdentityAutoConfiguration {

    @Bean
    public FilterRegistrationBean<IdentityHeaderFilter> identityHeaderFilterRegistration() {
        FilterRegistrationBean<IdentityHeaderFilter> registration =
            new FilterRegistrationBean<>(new IdentityHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
