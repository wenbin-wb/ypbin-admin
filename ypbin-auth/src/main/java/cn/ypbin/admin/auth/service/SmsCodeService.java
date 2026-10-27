/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.service;

import cn.ypbin.admin.auth.support.AuthConfigReader;
import cn.ypbin.admin.common.util.CachedCounterReader;
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.model.dto.SysUserDto;
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
 * <p>短信开关、模板与厂商配置存于系统参数（sys_config，由 system-svc 维护），
 * 本服务经 {@link AuthConfigReader} 读取；验证码缓存走 Redis（starter-cache）。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final String CODE_PREFIX = "sms:code:";
    private static final String COOLDOWN_PREFIX = "sms:cooldown:";
    /** 校验失败计数前缀（按手机号） */
    private static final String VERIFY_FAIL_PREFIX = "sms:verify:fail:";
    /** 同一验证码允许的最大错误次数，超过即作废并要求重新获取 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthConfigReader configReader;
    private final CacheService cacheService;

    /**
     * 发送验证码到指定手机。验证码随机 6 位数字，TTL 由 sys_config SMS_CODE_EXPIRE_SECONDS 控制。
     */
    public void sendCode(String phone) {
        String normalizedPhone = normalizePhone(phone);
        int cooldownSeconds = configReader.getInt("SMS_CODE_COOLDOWN_SECONDS", 60);
        int expireSeconds = configReader.getInt("SMS_CODE_EXPIRE_SECONDS", 300);
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
            String templateId = configReader.getString("SMS_TEMPLATE_ID", "");
            cacheService.set(codeKey, code, Duration.ofSeconds(expireSeconds));
            // 新验证码 = 新的尝试预算：不清旧计数的话，用户按提示「重新获取」后仍会被旧计数锁死
            cacheService.delete(VERIFY_FAIL_PREFIX + normalizedPhone);
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
                configReader.getInt("SMS_CODE_EXPIRE_SECONDS", 300)));
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

    /**
     * 按手机号查询用户（经 Feign 调 system-svc）。
     *
     * @param phone 手机号
     * @return 用户，未注册时为空
     */
    public SysUserDto getUserByPhone(String phone) {
        String normalizedPhone = normalizePhone(phone);
        return SysCache.getUserByPhone(normalizedPhone);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        return phone.trim();
    }
}
