/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.messaging.util.SmsUtils;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
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
    private static final String COOLDOWN_PREFIX = "sms:cooldown:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysConfigService configService;
    private final CacheService cacheService;

    /**
     * 发送验证码到指定手机。验证码随机 6 位数字，TTL 由 sys_config SMS_CODE_EXPIRE_SECONDS 控制。
     */
    public void sendCode(String phone) {
        String normalizedPhone = normalizePhone(phone);
        int cooldownSeconds = configService.getInt("SMS_CODE_COOLDOWN_SECONDS", 60);
        int expireSeconds = configService.getInt("SMS_CODE_EXPIRE_SECONDS", 300);
        if (cooldownSeconds <= 0 || expireSeconds <= 0) {
            throw new BusinessException("短信验证码有效期和发送冷却必须大于 0 秒");
        }
        String cooldownKey = COOLDOWN_PREFIX + normalizedPhone;
        String cooldownOwner = UUID.randomUUID().toString();
        if (!cacheService.setIfAbsent(
            cooldownKey, cooldownOwner, Duration.ofSeconds(cooldownSeconds))) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeKey = CODE_PREFIX + normalizedPhone;
        try {
            String templateId = configService.getString("SMS_TEMPLATE_ID", "");
            cacheService.set(codeKey, code, Duration.ofSeconds(expireSeconds));
            SmsUtils.sendByTemplate(normalizedPhone, templateId, Map.of("code", code));
        } catch (RuntimeException e) {
            cacheService.compareAndDelete(codeKey, code);
            cacheService.compareAndDelete(cooldownKey, cooldownOwner);
            throw e;
        }
    }

    /**
     * 校验验证码，通过后消费（删除缓存），不通过抛业务异常。
     */
    public void verify(String phone, String code) {
        String key = CODE_PREFIX + normalizePhone(phone);
        if (cacheService.compareAndDelete(key, code)) {
            return;
        }
        if (!cacheService.exists(key)) {
            throw new BusinessException("验证码已过期或已使用");
        }
        throw new BusinessException("验证码错误");
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        return phone.trim();
    }
}
