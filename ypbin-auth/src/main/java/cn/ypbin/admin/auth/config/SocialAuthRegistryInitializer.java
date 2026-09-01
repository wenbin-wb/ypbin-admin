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
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.social.core.SocialRequestRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.request.AuthRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 第三方登录平台注册初始化器（auth 域）。
 *
 * <p>auth-svc 与 system-svc 各自持有独立的 {@link SocialRequestRegistry} 实例（进程内不共享），
 * 本初始化器在启动时经 Feign 从 system-svc 拉取已启用平台的授权配置，构建 JustAuth 授权请求
 * 注册进本服务的注册表，供授权跳转/回调使用。配置变更后由 system-svc 侧发布的事件驱动
 * 重新注册（auth 侧通过后续拉取覆盖）。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Component
@RequiredArgsConstructor
public class SocialAuthRegistryInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SocialAuthRegistryInitializer.class);

    private final ISystemClient systemClient;
    private final SocialAuthRequestFactory requestFactory;
    private final SocialRequestRegistry socialRequestRegistry;

    @Override
    public void run(ApplicationArguments args) {
        try {
            R<List<SocialAuthConfig>> result = systemClient.listSocialAuthConfigs();
            if (result == null || result.getData() == null) {
                return;
            }
            for (SocialAuthConfig config : result.getData()) {
                if (!config.isEnabled()) {
                    continue;
                }
                AuthRequest request = requestFactory.create(config.getSource(), config.getClientId(),
                    config.getClientSecret(), config.getRedirectUri(), config.getPublicKey());
                socialRequestRegistry.register(config.getSource(), request);
            }
            log.info("第三方登录平台注册完成，共 {} 个启用平台。", result.getData().stream()
                .filter(SocialAuthConfig::isEnabled).count());
        } catch (RuntimeException e) {
            // system-svc 未就绪时跳过注册（登录时按需报错暴露），不影响服务启动
            log.error("启动时加载第三方登录平台配置失败", e);
        }
    }
}
