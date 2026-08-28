/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin;

import cn.ypbin.admin.bootstrap.SecurityBootstrapProperties;
import cn.ypbin.admin.common.config.LicenseIssuerProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ypbin-admin 启动类。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SecurityBootstrapProperties.class, LicenseIssuerProperties.class})
@MapperScan("cn.ypbin.admin.**.mapper")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
