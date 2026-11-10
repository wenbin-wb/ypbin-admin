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

import cn.ypbin.starter.log.core.IpLocationResolver;
import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * IP 归属地解析的装配（由各服务启动类 {@code @Import} 引入）。
 *
 * <p><strong>为什么不用 {@code @Component}</strong>：微服务版各服务的组件扫描范围是
 * {@code cn.ypbin.admin.<服务>}（外加 {@code cn.ypbin.admin.system.api}），不包含
 * {@code cn.ypbin.admin.common}，common 里的组件不会自动生效。这里沿用
 * {@code RemoteIngestClientConfiguration} 的注册方式：宿主启动类显式 {@code @Import}，
 * Bean 定义在自动配置之前完成注册，starter 的默认空解析器（{@code @ConditionalOnMissingBean}）
 * 随之让位。</p>
 *
 * <p><strong>为什么 auth / ai / system 都要引入</strong>：操作日志的 {@code location} 由各服务
 * 自己的 {@code LogCollector} 采集；auth 还要为在线用户终端写入归属地。只在一个服务装配会让其余
 * 服务的归属地仍为空——前端是同一套，字段不能时有时无。</p>
 *
 * @author wenbin
 * @since 2026-11-08
 */
@Configuration(proxyBeanMethods = false)
public class IpLocationConfiguration {

    /**
     * IP 归属地解析器（敏感度低、纯本地计算，无需缓存与会话）。
     *
     * @param searcher ip2region 搜索器（由 {@code mica-ip2region} 自动配置提供）
     * @return 归属地解析器
     */
    @Bean
    @ConditionalOnMissingBean(IpLocationResolver.class)
    public IpLocationResolver ipLocationResolver(Ip2regionSearcher searcher) {
        return new Ip2regionLocationResolver(searcher);
    }
}
