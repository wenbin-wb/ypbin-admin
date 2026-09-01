/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Ai 服务启动器（微服务版）。
 *
 * @author wenbin
 * @since 2026-09-01
 */
@EnableFeignClients(basePackages = "cn.ypbin.admin.system.api.feign")
@SpringBootApplication(scanBasePackages = {"cn.ypbin.admin.ai", "cn.ypbin.admin.system.api"})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
