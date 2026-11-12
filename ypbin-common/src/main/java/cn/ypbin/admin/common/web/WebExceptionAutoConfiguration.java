/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Web 异常处理相关的自动配置。
 *
 * <p>各服务的组件扫描根包是 {@code cn.ypbin.admin.<服务名>}，扫不到本模块的
 * {@code cn.ypbin.admin.common.web}，因此这里的 advice 必须显式注册为 Bean，
 * 否则 {@link AsyncDisconnectExceptionHandler} 只在编译期存在、运行期不会生效。</p>
 *
 * @author wenbin
 * @since 2026-11-12
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebExceptionAutoConfiguration {

    /**
     * 异步连接断开异常处理器。
     *
     * @return advice 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AsyncDisconnectExceptionHandler asyncDisconnectExceptionHandler() {
        return new AsyncDisconnectExceptionHandler();
    }
}
