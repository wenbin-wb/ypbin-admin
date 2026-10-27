/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service.impl;

import cn.ypbin.admin.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.common.util.CachedCounterReader;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 知识库「公开访问」（分享 / 网页挂件）的公共支撑。
 *
 * <p>两个入口的令牌生成、匿名租户绑定问答、访问密码校验此前各写一份，已经出现过
 * 「分享侧换算法、挂件侧漏改」的风险。这里收成单一实现：新增公开入口时只复用它，
 * 不再复制 {@link SecureRandom}、超时常量与租户包裹逻辑。</p>
 *
 * <p><b>密码存储</b>：新设置的访问密码使用 {@link PasswordEncoderUtil}（BCrypt，含盐）。
 * 历史数据是裸 SHA-256（无盐），仍可校验以兼容存量分享链接；下次重新设置密码时自然升级。
 * 校验失败按「令牌 + 客户端 IP」计数，连续 {@value #PWD_MAX_FAILURES} 次锁定
 * {@value #PWD_FAIL_WINDOW_MINUTES} 分钟。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
@Component
@RequiredArgsConstructor
public class AiPublicAccessSupport {

    private static final Logger log = LoggerFactory.getLogger(AiPublicAccessSupport.class);

    /** 复用同一 SecureRandom：每次 new 既浪费熵源，也可能产生相关性 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 令牌字节数（128 bit） */
    private static final int TOKEN_BYTES = 16;

    /** 匿名问答同步等待上限：远程模型无响应时及时失败，避免请求线程无限阻塞 */
    private static final Duration ANSWER_BLOCK_TIMEOUT = Duration.ofSeconds(120);

    /** 访问密码失败计数键前缀 */
    private static final String PWD_FAIL_PREFIX = "ai:public:pwd:fail:";

    /** 访问密码连续失败上限 */
    private static final int PWD_MAX_FAILURES = 5;

    /** 访问密码锁定时长（分钟） */
    private static final int PWD_FAIL_WINDOW_MINUTES = 10;

    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    /**
     * 生成公开访问令牌（128 bit 随机数的十六进制串）。
     *
     * @return 令牌
     */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 在知识库所属租户内执行匿名 RAG 问答。
     *
     * @param kb             已通过令牌校验的知识库
     * @param conversationId 会话标识（分享/挂件各用自己的前缀，互不串上下文）
     * @param question       问题
     * @param scene          日志场景名（分享/挂件）
     * @return 回答文本
     */
    public String answer(AiKnowledgeBase kb, String conversationId, String question, String scene) {
        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService == null) {
            throw new BusinessException("AI 对话服务未配置，请先配置对话模型");
        }
        try {
            // 匿名请求无登录上下文：显式绑定知识库所属租户，保证 RAG 检索与 AI 调用在正确租户内
            List<String> tokens = TenantContext.executeWithTenant(kb.getTenantId(),
                () -> aiChatService.chatWithKnowledge(
                        conversationId, question, String.valueOf(kb.getId()))
                    .collectList()
                    .block(ANSWER_BLOCK_TIMEOUT));
            return tokens == null ? "" : String.join("", tokens);
        } catch (IllegalStateException e) {
            // 模型未配置/密钥缺失等环境问题：记录日志并向调用方暴露明确的业务错误（非静默降级）
            // 不记录 token：它是访问该公开入口的凭据，落日志等于泄露；改用知识库 ID 关联排查
            log.warn("[ypbin-ai] {}问答失败: kbId={} err={}", scene, kb.getId(), e.getMessage(), e);
            throw new BusinessException("AI 模型未配置，请在【AI 配置】中添加对话模型");
        }
    }

    /**
     * 编码访问密码。
     *
     * @param password 明文密码
     * @return BCrypt 哈希；空值返回 null（表示不设密码）
     */
    public String encodePassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        return PasswordEncoderUtil.encode(password);
    }

    /**
     * 校验访问密码（含失败次数限制）。
     *
     * <p><b>失败计数按「令牌 + 客户端 IP」隔离</b>：令牌就是分享链接本身，对所有访问者公开。
     * 若只按令牌计数，任何人拿到链接后提交 5 次错误密码，就能把真正的访问者全部锁在
     * 「请 10 分钟后再试」之外（针对公开入口的拒绝服务）。按 IP 隔离后，锁定只影响
     * 发起爆破的那个来源。IP 与 {@code @RateLimit} 同源（{@code WebRequestUtils.ip()}，
     * 信任网关注入的转发头），不另立一套取值口径。</p>
     *
     * @param token     分享/挂件令牌（用于隔离失败计数）
     * @param clientIp  调用方客户端 IP（由 Controller 从请求上下文取，可空）
     * @param stored    库内密码哈希
     * @param submitted 调用方提交的明文密码
     */
    public void verifyPassword(String token, String clientIp, String stored, String submitted) {
        if (stored == null || stored.isBlank()) {
            return;
        }
        String source = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        String failKey = PWD_FAIL_PREFIX + token + ":" + source;
        long failures = readFailures(failKey);
        if (failures >= PWD_MAX_FAILURES) {
            throw new BusinessException("访问密码错误次数过多，请 " + PWD_FAIL_WINDOW_MINUTES + " 分钟后再试");
        }
        boolean matched = matches(stored, submitted);
        if (!matched) {
            long current = CacheUtils.increment(failKey, 1);
            if (current == 1) {
                CacheUtils.expire(failKey, Duration.ofMinutes(PWD_FAIL_WINDOW_MINUTES));
            }
            throw new BusinessException("访问密码错误");
        }
        // 成功即清零，避免正常用户偶发输错后累计到锁定
        CacheUtils.delete(failKey);
    }

    /**
     * 比较密码：新数据为 BCrypt，历史数据为裸 SHA-256（兼容存量分享链接）。
     *
     * @param stored    库内哈希
     * @param submitted 明文
     * @return 是否匹配
     */
    private static boolean matches(String stored, String submitted) {
        if (submitted == null) {
            return false;
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return PasswordEncoderUtil.matches(submitted, stored);
        }
        return constantTimeEquals(stored, sha256(submitted));
    }

    private static long readFailures(String key) {
        // 与短信验证码失败计数共用同一解析实现（缓存类型漂移时不会只修好一处）
        return CachedCounterReader.read(CacheUtils.get(key, Object.class));
    }

    /** 历史密码的 SHA-256（Hex）；仅用于兼容读取 */
    private static String sha256(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /** 常量时间比较，避免时序侧信道 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }
}
