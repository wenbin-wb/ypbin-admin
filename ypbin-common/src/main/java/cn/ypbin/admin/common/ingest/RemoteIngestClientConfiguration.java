/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.ingest;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.log.spi.RemoteLogIngestClient;
import cn.ypbin.starter.tracking.spi.RemoteTrackIngestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 跨服务「日志 / 埋点」上报端口的宿主适配（把 {@link ISystemClient} 适配成 starter 的两个窄 SPI）。
 *
 * <p><strong>starter 4.1.0 起，上报链路整条下沉</strong>：{@code ypbin-starter-log} /
 * {@code ypbin-starter-tracking} 自带 {@code RemoteLogAutoConfiguration} /
 * {@code RemoteTrackAutoConfiguration}，只要容器里存在 {@link RemoteLogIngestClient} /
 * {@link RemoteTrackIngestClient} 就装配各自的远端实现，且 {@code @AutoConfigureBefore} 的顺序知识
 * <strong>留在 starter 内部</strong>。宿主因此只需要写这两个「一行方法引用」的适配 Bean——
 * 旧形态（自建 {@code RemoteLogAutoConfiguration} + {@code beforeName} 指向 starter 内部类名 +
 * 自建 {@code RemoteLogDao}/{@code RemoteTrackEventSink} + 自建两个 provider）已全部删除，
 * 避免一个类名拼错就静默退回「只打日志不落库」。</p>
 *
 * <p><strong>为什么是普通 {@code @Configuration} 而不是自动配置类</strong>：starter 侧用的是
 * {@code @ConditionalOnBean(RemoteLogIngestClient.class)}。{@code @ConditionalOnBean} 只看得见
 * <b>已经注册完成</b>的 Bean 定义；若本配置也做成自动配置且没有与 starter 的自动配置排好顺序，
 * 条件会先于这两个 Bean 求值而恒假，静默退回只打印的默认实现（正是本轮要消除的失效形态）。
 * 由宿主应用通过 {@code @Import} 引入普通 {@code @Configuration}，其 Bean 定义在所有自动配置
 * 处理之前就已完成注册，条件必然成立——这正是 starter 文档要求的注册方式。</p>
 *
 * <p><strong>谁需要引入本配置</strong>：只有「没有本地 {@code sys_log}/{@code sys_track_event} 数据源、
 * 需要把日志/埋点上报给 system」的下游服务（本仓为 auth 与 ai）。system 自己已组件扫描出
 * {@code DbLogProviders.DbLogDao} 与 {@code SysTrackEventSink} 两个本地落库实现，starter 的远端实现
 * 与本地实现靠 {@code @ConditionalOnMissingBean} 互斥；它不引入本配置也就完全不涉及远端上报，
 * 不会出现「事件 Feign 回自己」的自我回环。</p>
 *
 * <p><strong>失败语义</strong>：这两个适配器只做方法引用，异常与失败 {@code R} 原样透传，
 * 由 starter 的 {@code RemoteLogDao}/{@code RemoteTrackEventSink} 记完整堆栈并上抛（禁静默降级）。</p>
 *
 * <p><strong>为什么把两个 Bean 关在各自的嵌套静态类里</strong>：两个 SPI 分别来自 starter-log 与
 * starter-tracking，而这两者在 {@code ypbin-common} 里是 {@code optional}（避免强推给所有 common
 * 消费者）。类级 {@code @ConditionalOnClass} 在内省 {@code @Bean} 方法签名<b>之前</b>求值，
 * 因此「没引该模块的宿主」不会走到解析方法签名的步骤、也就不会 {@code NoClassDefFoundError}；
 * 若把条件写在方法上则做不到这一点（外层类仍会被内省）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@Configuration(proxyBeanMethods = false)
public class RemoteIngestClientConfiguration {

    /**
     * 操作日志上报端口的适配（仅类路径存在 starter-log 时生效）。
     *
     * <p>{@code ISystemClient} 的返回类型已是 starter 的 {@code R<Void>}，与 SPI 签名一致，
     * 故直接方法引用即可，不需要额外包装或字段映射。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RemoteLogIngestClient.class)
    static class LogIngestConfiguration {

        /**
         * 操作日志上报端口（{@code ISystemClient#ingestLog} 的适配）。
         *
         * @param systemClient 系统服务 Feign 客户端
         * @return 日志上报端口
         */
        @Bean
        @ConditionalOnMissingBean
        RemoteLogIngestClient remoteLogIngestClient(ISystemClient systemClient) {
            return systemClient::ingestLog;
        }
    }

    /**
     * 埋点事件上报端口的适配（仅类路径存在 starter-tracking 时生效）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RemoteTrackIngestClient.class)
    static class TrackIngestConfiguration {

        /**
         * 埋点事件上报端口（{@code ISystemClient#ingestTrackEvents} 的适配）。
         *
         * @param systemClient 系统服务 Feign 客户端
         * @return 埋点上报端口
         */
        @Bean
        @ConditionalOnMissingBean
        RemoteTrackIngestClient remoteTrackIngestClient(ISystemClient systemClient) {
            return systemClient::ingestTrackEvents;
        }
    }
}
