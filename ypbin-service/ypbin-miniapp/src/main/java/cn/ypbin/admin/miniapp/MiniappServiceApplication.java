/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 微信小程序业务服务启动类（牌账清 CardTab & 家庭菜单 FamilyMenu）。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "cn.ypbin.admin")
@SpringBootApplication(scanBasePackages = "cn.ypbin")
@MapperScan("cn.ypbin.admin.miniapp.**.mapper")
public class MiniappServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniappServiceApplication.class, args);
    }
}
