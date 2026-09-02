/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.provider;

import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 微服务租户来源自动装配。
 *
 * <p>仅在类路径同时存在租户扩展与身份头上下文时注册，避免 auth 等不需要租户拦截的
 * 服务被额外装配。</p>
 *
 * @author wenbin
 * @since 2026-09-03
 */
@AutoConfiguration
@ConditionalOnClass({TenantProvider.class, IdentityContext.class})
public class MicroserviceTenantAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TenantProvider.class)
    public MicroserviceTenantProvider microserviceTenantProvider() {
        return new MicroserviceTenantProvider();
    }
}
