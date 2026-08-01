/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.messaging.util.SmsUtils;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 短信验证码服务：发码→Redis 存码带 TTL→校验消费（一次性）。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final String CODE_PREFIX = "sms:code:";

    private final SysConfigService configService;
    private final CacheService cacheService;

    /**
     * 发送验证码到指定手机。验证码随机 6 位数字，TTL 由 sys_config SMS_CODE_EXPIRE_SECONDS 控制。
     */
    public void sendCode(String phone) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        int expireSeconds = configService.getInt("SMS_CODE_EXPIRE_SECONDS", 300);
        String templateId = configService.getString("SMS_TEMPLATE_ID", "");
        SmsUtils.sendByTemplate(phone, templateId, Map.of("code", code));
        cacheService.set(CODE_PREFIX + phone, code, Duration.ofSeconds(expireSeconds));
    }

    /**
     * 校验验证码，通过后消费（删除缓存），不通过抛业务异常。
     */
    public void verify(String phone, String code) {
        String cached = cacheService.get(CODE_PREFIX + phone, String.class);
        if (cached == null) {
            throw new BusinessException("验证码已过期");
        }
        if (!cached.equals(code)) {
            throw new BusinessException("验证码错误");
        }
        cacheService.delete(CODE_PREFIX + phone);
    }
}
