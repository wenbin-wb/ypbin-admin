/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.bootstrap;

import cn.ypbin.starter.data.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 一次性平台管理员初始化执行器。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Component
@RequiredArgsConstructor
public class SecurityBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityBootstrapRunner.class);

    private final SecurityBootstrapProperties properties;
    private final SecurityBootstrapService bootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        String owner = IdGenerator.simpleUuid();
        if (bootstrapService.initialize(properties, owner)) {
            log.info("平台管理员一次性初始化已完成，请关闭 ypbin.admin.bootstrap.enabled");
        } else {
            log.info("平台管理员一次性初始化已由其他实例执行或完成");
        }
    }
}
