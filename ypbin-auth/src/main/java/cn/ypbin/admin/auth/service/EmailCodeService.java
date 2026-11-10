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
import cn.ypbin.admin.common.constant.RegisterConfigKeys;
import cn.ypbin.admin.common.util.CachedCounterReader;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.req.MailSendReq;
import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 邮箱注册验证码服务：发码 → Redis 存码带 TTL → 校验消费（一次性）。
 *
 * <p><b>与短信验证码同一套安全语义</b>：发送冷却、过期、一次性消费、同一验证码最多允许 5 次错误
 * （超过即作废，防止逐位枚举）。区别只在投递通道：本服务经 {@code ISystemClient#sendMail}
 * 调 system 的 JavaMail 能力（auth 无数据源，也读不到 {@code MAIL_*} 配置）。</p>
 *
 * <p><b>为什么单独一套缓存键</b>：短信与邮箱是两条独立通道，同一用户可能同时持有两种验证码；
 * 共用键会把后发的码覆盖先发的码，且错误计数互相污染。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    /** 验证码缓存键前缀（按邮箱） */
    private static final String CODE_PREFIX = "email:register:code:";

    /** 发送冷却键前缀（按邮箱） */
    private static final String COOLDOWN_PREFIX = "email:register:cooldown:";

    /** 校验失败计数前缀（按邮箱） */
    private static final String VERIFY_FAIL_PREFIX = "email:register:verify:fail:";

    /** 同一验证码允许的最大错误次数，超过即作废并要求重新获取 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    /** 验证码上界（6 位数字） */
    private static final int CODE_BOUND = 1_000_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthConfigReader configReader;
    private final CacheService cacheService;
    private final ISystemClient systemClient;

    /**
     * 发送邮箱验证码。验证码随机 6 位数字，TTL 由 {@code EMAIL_CODE_EXPIRE_SECONDS} 控制。
     *
     * @param email 收件邮箱
     */
    public void sendCode(String email) {
        String target = normalizeEmail(email);
        int cooldownSeconds = configReader.getInt(
            RegisterConfigKeys.EMAIL_CODE_COOLDOWN_SECONDS, 60);
        int expireSeconds = configReader.getInt(
            RegisterConfigKeys.EMAIL_CODE_EXPIRE_SECONDS, 300);
        if (cooldownSeconds <= 0 || expireSeconds <= 0) {
            throw new BusinessException("邮箱验证码有效期和发送冷却必须大于 0 秒");
        }
        String cooldownKey = COOLDOWN_PREFIX + target;
        String cooldownOwner = UUID.randomUUID().toString();
        if (!cacheService.setIfAbsent(cooldownKey, cooldownOwner,
            Duration.ofSeconds(cooldownSeconds))) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(CODE_BOUND));
        String codeKey = CODE_PREFIX + target;
        try {
            cacheService.set(codeKey, code, Duration.ofSeconds(expireSeconds));
            // 新验证码 = 新的尝试预算
            cacheService.delete(VERIFY_FAIL_PREFIX + target);
            sendMail(target, code, expireSeconds);
        } catch (RuntimeException e) {
            // 发送失败不能留下一个用户永远收不到的验证码（否则冷却期内无法重发）
            cacheService.compareAndDelete(codeKey, code);
            cacheService.compareAndDelete(cooldownKey, cooldownOwner);
            throw e;
        }
    }

    /**
     * 校验邮箱验证码，通过后消费（删除缓存），不通过抛业务异常。
     *
     * @param email 收件邮箱
     * @param code  用户提交的验证码
     */
    public void verify(String email, String code) {
        String target = normalizeEmail(email);
        String key = CODE_PREFIX + target;
        String failKey = VERIFY_FAIL_PREFIX + target;
        if (readFailures(failKey) >= MAX_VERIFY_ATTEMPTS) {
            // 达到上限后直接作废验证码：避免一次发码被用于长期逐位枚举
            cacheService.delete(key);
            throw new BusinessException("验证码错误次数过多，请重新获取");
        }
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("请输入邮箱验证码");
        }
        if (cacheService.compareAndDelete(key, code.trim())) {
            cacheService.delete(failKey);
            return;
        }
        if (!cacheService.exists(key)) {
            throw new BusinessException("验证码已过期或已使用");
        }
        long failures = cacheService.increment(failKey, 1);
        if (failures == 1) {
            cacheService.expire(failKey, Duration.ofSeconds(
                configReader.getInt(RegisterConfigKeys.EMAIL_CODE_EXPIRE_SECONDS, 300)));
        }
        if (failures >= MAX_VERIFY_ATTEMPTS) {
            cacheService.delete(key);
            throw new BusinessException("验证码错误次数过多，请重新获取");
        }
        throw new BusinessException("验证码错误");
    }

    /**
     * 经 system 的 JavaMail 能力投递验证码邮件。
     *
     * @param email         收件邮箱
     * @param code          验证码
     * @param expireSeconds 有效期（秒）
     */
    private void sendMail(String email, String code, int expireSeconds) {
        MailSendReq req = new MailSendReq();
        req.setTo(email);
        req.setSubject("注册验证码");
        req.setContent("您的注册验证码为：" + code + "，" + (expireSeconds / 60)
            + " 分钟内有效。如非本人操作，请忽略本邮件。");
        R<Void> resp = systemClient.sendMail(req);
        if (resp == null || !resp.isSuccess()) {
            // 不能把「邮件没发出去」当成功：调用方会因此等待一个永远收不到的验证码
            String message = resp == null || !StringUtils.hasText(resp.getMessage())
                ? "验证码邮件发送失败，请稍后重试" : resp.getMessage();
            throw new BusinessException(message);
        }
    }

    /**
     * 读取失败计数（缓存反序列化类型不稳定，统一走公共解析）。
     *
     * @param key 计数键
     * @return 当前失败次数
     */
    private long readFailures(String key) {
        return CachedCounterReader.read(cacheService.get(key, Object.class));
    }

    /**
     * 邮箱归一：去空白 + 小写（缓存键与校验口径一致，避免大小写差异导致「码不对」）。
     *
     * @param email 原始邮箱
     * @return 归一后的邮箱
     */
    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException("邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
