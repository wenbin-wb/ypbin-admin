/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.provider;

import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.starter.messaging.mail.MailConfig;
import cn.ypbin.starter.messaging.mail.MailConfigProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 邮件配置来源：从系统参数读取 SMTP 配置，后台改配置即时生效。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
@RequiredArgsConstructor
public class DbMailConfigProvider implements MailConfigProvider {

    private final SysConfigService configService;

    @Override
    public MailConfig getConfig() {
        MailConfig config = new MailConfig();
        config.setHost(configService.getString("MAIL_HOST", ""));
        config.setPort(configService.getInt("MAIL_PORT", 465));
        config.setUsername(configService.getString("MAIL_USERNAME", ""));
        config.setPassword(configService.getString("MAIL_PASSWORD", ""));
        config.setFrom(configService.getString("MAIL_FROM", ""));
        config.setFromName(configService.getString("MAIL_FROM_NAME", ""));
        config.setSslEnabled(configService.getBoolean("MAIL_SSL_ENABLED", true));
        return config;
    }
}
