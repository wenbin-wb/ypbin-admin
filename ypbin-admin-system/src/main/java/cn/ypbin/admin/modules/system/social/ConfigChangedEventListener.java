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

import cn.ypbin.admin.modules.system.config.sms.SmsReadConfigDbImpl;
import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.starter.social.core.SocialRequestRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 配置提交完成后的同步处理器。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Component
public class ConfigChangedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangedEventListener.class);

    private final SysConfigService configService;
    private final SmsReadConfigDbImpl smsReadConfigDb;
    private final SocialRequestRegistry socialRequestRegistry;

    public ConfigChangedEventListener(SysConfigService configService,
                                     SmsReadConfigDbImpl smsReadConfigDb,
                                     SocialRequestRegistry socialRequestRegistry) {
        this.configService = configService;
        this.smsReadConfigDb = smsReadConfigDb;
        this.socialRequestRegistry = socialRequestRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfigChanged(ConfigChangedEvent event) {
        try {
            configService.refreshCache();
            if (event.smsChanged()) {
                smsReadConfigDb.reload();
            }
        } catch (RuntimeException e) {
            log.error("系统参数提交后同步失败", e);
            throw e;
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSocialConfigChanged(SocialConfigChangedEvent event) {
        try {
            configService.refreshCache();
            if (event.enabled()) {
                socialRequestRegistry.register(event.source(), event.request());
            } else {
                socialRequestRegistry.remove(event.source());
            }
        } catch (RuntimeException e) {
            log.error("第三方登录配置提交后同步失败，source={}", event.source(), e);
            throw e;
        }
    }
}
