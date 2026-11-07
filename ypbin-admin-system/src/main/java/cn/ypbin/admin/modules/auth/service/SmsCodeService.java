/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.service;

import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.admin.common.util.CachedCounterReader;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.messaging.util.SmsUtils;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);

    private static final String CODE_PREFIX = "sms:code:";
    private static final String COOLDOWN_PREFIX = "sms:cooldown:";
    /** 验证码校验失败计数键前缀（按手机号，防一次发码被用于逐位枚举）。 */
    private static final String VERIFY_FAIL_PREFIX = "sms:verify:fail:";
    /** 单个验证码允许的最大校验失败次数。 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;
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
            // 新验证码 = 新的尝试预算：不清旧计数的话，用户按提示「重新获取」后仍会被旧计数锁死
            cacheService.delete(VERIFY_FAIL_PREFIX + normalizedPhone);
            // 厂商返回 false 时必须失败：若当作成功，验证码留在缓存而用户永远收不到短信
            boolean sent = SmsUtils.sendByTemplate(normalizedPhone, templateId, Map.of("code", code));
            if (!sent) {
                log.error("短信发送失败（厂商返回 false）：phone={}, templateId={}",
                    maskPhone(normalizedPhone), templateId);
                throw new BusinessException("短信发送失败，请检查短信厂商配置");
            }
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
        String normalizedPhone = normalizePhone(phone);
        String key = CODE_PREFIX + normalizedPhone;
        String failKey = VERIFY_FAIL_PREFIX + normalizedPhone;
        if (readFailures(failKey) >= MAX_VERIFY_ATTEMPTS) {
            // 达到上限后直接作废验证码：避免一次发码被用于长期逐位枚举
            cacheService.delete(key);
            throw new BusinessException("验证码错误次数过多，请重新获取");
        }
        if (cacheService.compareAndDelete(key, code)) {
            cacheService.delete(failKey);
            return;
        }
        if (!cacheService.exists(key)) {
            throw new BusinessException("验证码已过期或已使用");
        }
        long failures = cacheService.increment(failKey, 1);
        if (failures == 1) {
            cacheService.expire(failKey, Duration.ofSeconds(
                configService.getInt("SMS_CODE_EXPIRE_SECONDS", 300)));
        }
        if (failures >= MAX_VERIFY_ATTEMPTS) {
            cacheService.delete(key);
            throw new BusinessException("验证码错误次数过多，请重新获取");
        }
        throw new BusinessException("验证码错误");
    }

    /**
     * 读取失败计数（缓存反序列化类型不稳定，统一走公共解析，与公开访问密码计数同一实现）。
     *
     * @param key 计数键
     * @return 当前失败次数
     */
    private long readFailures(String key) {
        return CachedCounterReader.read(cacheService.get(key, Object.class));
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        return phone.trim();
    }

    /** 日志脱敏：保留前 3 位与后 4 位。 */
    private static String maskPhone(String phone) {
        return phone.length() <= 7 ? "****"
            : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
