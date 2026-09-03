/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.config.sms;

import lombok.RequiredArgsConstructor;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 短信配置加载器：应用启动后按数据库短信配置注册 sms4j 短信实例。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Component
@RequiredArgsConstructor
public class SmsConfigLoader implements ApplicationRunner {

    private final SmsReadConfigDbImpl smsReadConfigDb;

    @Override
    public void run(ApplicationArguments args) {
        SmsFactory.createSmsBlend(smsReadConfigDb);
    }
}
