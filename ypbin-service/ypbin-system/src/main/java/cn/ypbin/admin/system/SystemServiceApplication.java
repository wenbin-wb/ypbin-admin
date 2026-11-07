/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system;

import cn.ypbin.admin.common.config.LicenseIssuerProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * System 服务启动器（微服务版）。
 *
 * @author wenbin
 * @since 2026-09-01
 */
@SpringBootApplication
@MapperScan("cn.ypbin.admin.**.mapper")
@EnableConfigurationProperties(LicenseIssuerProperties.class)
public class SystemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemServiceApplication.class, args);
    }
}
