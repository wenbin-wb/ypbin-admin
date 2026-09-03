/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.social;

import cn.ypbin.admin.modules.system.model.resp.SocialConfigResp;
import cn.ypbin.admin.modules.system.service.SocialConfigService;
import cn.ypbin.starter.social.core.SocialRequestRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 第三方登录平台启动初始化器。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Configuration
@RequiredArgsConstructor
public class SocialAuthConfiguration implements ApplicationRunner {

    private final SocialConfigService socialConfigService;
    private final SocialRequestRegistry socialRequestRegistry;

    @Override
    public void run(ApplicationArguments args) {
        for (SocialConfigResp config : socialConfigService.listConfigs()) {
            if (Boolean.TRUE.equals(config.getEnabled())) {
                socialRequestRegistry.register(config.getSource(),
                    socialConfigService.createEnabledRequest(config.getSource()));
            } else {
                socialRequestRegistry.remove(config.getSource());
            }
        }
    }
}
