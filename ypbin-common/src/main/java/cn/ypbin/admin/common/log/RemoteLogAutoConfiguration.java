/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.log;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.log.dao.LogDao;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 日志"跨服务上报"自动装配。
 *
 * <p><b>解决的问题：</b>starter 的 {@code LogAutoConfiguration.logDao()} 在宿主没提供 {@link LogDao} 时
 * 会安静地装配只打印不落库的 {@code DefaultLogDao}——auth/ai 都没有本地的落库实现，于是它们的
 * {@code @Log} 全部只进应用日志、{@code sys_log} 恒为空。本配置为这类服务补上"上报给 system"的实现。</p>
 *
 * <p><b>装配优先级：</b>{@code beforeName} 指向 starter 的 {@code LogAutoConfiguration}，
 * 使 {@link #remoteLogDao} 先于它的 {@code @ConditionalOnMissingBean} 判定完成注册；同时本方法自身也带
 * {@code @ConditionalOnMissingBean}，因此 <b>system</b>（已组件扫描出 {@code DbLogProviders.DbLogDao}）
 * 与任何自带实现的宿主都会自动退让，全容器始终只有一个 {@link LogDao}。</p>
 *
 * <p><b>依赖可选：</b>本配置只在类路径同时存在 {@code LogDao} 与 {@link ISystemClient} 时生效，
 * 故没有日志/Feign 能力的应用（如纯 WebFlux 网关）不受影响。</p>
 *
 * <p><b>fail-fast 边界：</b>若某应用同时具备 {@code LogDao} 与 {@link ISystemClient} 的<b>类</b>、
 * 却没有 {@link ISystemClient} 的 <b>Bean</b>（典型是漏了 {@code @EnableFeignClients}），启动会因
 * {@code remoteLogDao} 依赖不满足而显式失败。这是刻意选择：此处不能用
 * {@code @ConditionalOnBean(ISystemClient.class)} 兜底——自动配置的条件在
 * {@code ImportBeanDefinitionRegistrar}（Feign 客户端注册）之前求值，那样写会让本实现被误判为"没有
 * system 客户端"而静默退回"只打印不落库"，正好复现本类要修的那个静默失效。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@AutoConfiguration(beforeName = "cn.ypbin.starter.log.autoconfigure.LogAutoConfiguration")
@ConditionalOnClass({LogDao.class, ISystemClient.class})
@ConditionalOnProperty(prefix = "ypbin.log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RemoteLogAutoConfiguration {

    /**
     * 无本地落库实现时，用"上报 system"替换 starter 的只打印默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(LogDao.class)
    public LogDao remoteLogDao(ISystemClient systemClient) {
        return new RemoteLogDao(systemClient);
    }

    /**
     * 无本地操作人数据源时，从网关身份头取操作人。
     */
    @Bean
    @ConditionalOnMissingBean(LogUserProvider.class)
    public LogUserProvider identityHeaderLogUserProvider() {
        return new IdentityHeaderLogUserProvider();
    }

    /**
     * 无本地客户端信息数据源时，从 sa-token 登录会话取客户端 ID/类型/认证方式。
     *
     * <p>刻意不扩展网关身份头：网关只签发 id/username/tenantId/deptId/roles，新增头等于改动跨服务契约；
     * 而登录时写入 Account-Session 的 {@code LoginUser} 本就带这三项，读会话即可（写方 auth 与
     * 各读方共用同一份 Redis 会话存储）。</p>
     */
    @Bean
    @ConditionalOnMissingBean(LogClientProvider.class)
    public LogClientProvider sessionLogClientProvider() {
        return new SessionLogClientProvider();
    }
}
