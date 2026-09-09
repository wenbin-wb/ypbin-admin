/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.config;

import cn.ypbin.admin.auth.support.SocialAuthRequestFactory;
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.social.core.SocialRequestRegistry;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.request.AuthRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 第三方登录平台注册初始化与刷新（auth 域）。
 *
 * <p>auth-svc 与 system-svc 各自持有独立的 {@link SocialRequestRegistry} 实例（进程内不共享）。
 * 本组件启动时及每 5 分钟经 Feign 从 system-svc 全量重拉已启用平台配置并重建注册表，保证
 * 平台启停/密钥变更跨进程生效；同时提供 {@link #ensurePlatformRegistered(String)} 供授权跳转
 * 与回调前即时校验——平台已停用直接拒绝、配置有变即时用最新配置重建授权请求（读取带主动失效
 * 通知的共享缓存，system-svc 配置变更即清缓存键），避免仅依赖定时窗口的延迟。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Component
@RequiredArgsConstructor
public class SocialAuthRegistryInitializer implements ApplicationRunner {

    /** 注册表全量重拉周期（毫秒） */
    private static final long REFRESH_INTERVAL_MS = 300_000L;

    private static final Logger log = LoggerFactory.getLogger(SocialAuthRegistryInitializer.class);

    private final ISystemClient systemClient;
    private final SocialAuthRequestFactory requestFactory;
    private final SocialRequestRegistry socialRequestRegistry;

    @Override
    public void run(ApplicationArguments args) {
        try {
            refreshFromSystem();
        } catch (RuntimeException e) {
            // system-svc 未就绪时跳过注册（登录时按需报错暴露），不影响服务启动
            log.error("启动时加载第三方登录平台配置失败", e);
        }
    }

    /**
     * 全量重拉并重建注册表：删除已停用平台、注册/更新已启用平台。启动与定时刷新共用。
     */
    @Scheduled(fixedDelay = REFRESH_INTERVAL_MS, initialDelay = REFRESH_INTERVAL_MS)
    public void refreshFromSystem() {
        R<List<SocialAuthConfig>> result = systemClient.listSocialAuthConfigs();
        if (result == null || result.getData() == null) {
            log.warn("[auth] 拉取第三方登录平台配置结果为空，跳过本次刷新");
            return;
        }
        List<SocialAuthConfig> enabledConfigs = result.getData().stream()
            .filter(SocialAuthConfig::isEnabled)
            .toList();
        Set<String> enabledSources = enabledConfigs.stream()
            .map(SocialAuthConfig::getSource)
            .collect(Collectors.toSet());
        // 移除已被停用的平台
        for (String source : socialRequestRegistry.sources()) {
            if (!enabledSources.contains(source)) {
                socialRequestRegistry.remove(source);
            }
        }
        for (SocialAuthConfig config : enabledConfigs) {
            AuthRequest request = requestFactory.create(config.getSource(), config.getClientId(),
                config.getClientSecret(), config.getRedirectUri(), config.getPublicKey());
            socialRequestRegistry.register(config.getSource(), request);
        }
        log.info("第三方登录平台注册刷新完成，共 {} 个启用平台。", enabledConfigs.size());
    }

    /**
     * 授权跳转/回调前即时校验并注册指定平台。
     *
     * <p>读取共享缓存中的最新配置（system-svc 配置变更会主动失效该缓存键）：平台不存在或
     * 已停用时直接拒绝（防止停用平台仍可登录）；配置更新时用最新配置重建授权请求，无需等待
     * 定时刷新窗口。</p>
     *
     * @param source 平台标识
     * @return 最新授权请求
     */
    public AuthRequest ensurePlatformRegistered(String source) {
        SocialAuthConfig config = SysCache.getSocialAuthConfig(source);
        if (config == null) {
            throw new BusinessException("第三方登录平台未启用：" + source);
        }
        if (!config.isEnabled()) {
            throw new BusinessException("该第三方登录平台已停用：" + source);
        }
        AuthRequest request = requestFactory.create(config.getSource(), config.getClientId(),
            config.getClientSecret(), config.getRedirectUri(), config.getPublicKey());
        socialRequestRegistry.register(config.getSource(), request);
        return request;
    }
}
