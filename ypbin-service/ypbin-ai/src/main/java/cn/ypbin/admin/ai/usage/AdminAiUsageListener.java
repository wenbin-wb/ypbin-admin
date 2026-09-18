/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.usage;

import cn.ypbin.admin.ai.entity.AiChatSession;
import cn.ypbin.admin.ai.entity.AiUsageLog;
import cn.ypbin.admin.ai.mapper.AiChatSessionMapper;
import cn.ypbin.starter.ai.chat.usage.AiUsageInfo;
import cn.ypbin.starter.ai.chat.usage.AiUsageListener;
import cn.ypbin.starter.ai.chat.usage.AiUsageOutcome;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 用量回调的宿主实现：把 starter 上报的用量落到 {@code ai_usage_log}。
 *
 * <p><b>为什么需要宿主补齐租户/用户维度</b>：starter 的 {@link AiUsageInfo} 里<b>没有</b>用户与租户
 * （starter 的方法签名根本不携带用户信息），只能由宿主在自己的上下文里补。解析顺序：</p>
 * <ol>
 *   <li>同线程上下文：{@code TenantContext.getTenantId()}（显式绑定或 Reactor 快照传播），
 *       取不到再回退 {@code UserContext.getTenantId()}（微服务身份头/单体会话），
 *       用户维度取 {@link IdentityContext#getUserId()}（微服务身份头）。</li>
 *   <li>缺失时用 admin 自己的会话行补齐：{@code conversationId} 就是 {@code ai_chat_session} 主键，
 *       按主键查一次即可同时拿到 tenantId 与 userId。之所以必须有这一步：回调由 starter 在
 *       {@code doOnComplete/doOnError/doOnCancel} 上触发，线程多为 Reactor 工作线程，而
 *       {@code IdentityContext} 是普通 ThreadLocal、<b>未注册</b> Reactor {@code ThreadLocalAccessor}
 *       （只有 {@code TenantThreadLocalAccessor} 被注册），用户维度在回调线程上通常取不到；
 *       匿名入口（分享页/挂件/知识库检索问答）传入的 {@code share-<kbId>} 等非数字标识也走不到会话，
 *       此时 {@code userId} 如实落 {@code NULL}（就没有用户，不伪造）。</li>
 *   <li>仍然取不到租户：记 error 并放弃本次落库——{@code tenant_id} 是 NOT NULL，
 *       伪造成默认租户会把用量记到别人账上。</li>
 * </ol>
 *
 * <p><b>异常隔离</b>：本方法的任何异常都在内部捕获并 {@code log.error(..., ex)} 记录完整堆栈，
 * 绝不向 Reactor 链路抛出（starter 也会隔离一层，但宿主自己留痕才能定位是「落库失败」而不是
 * 「对话失败」）。</p>
 *
 * <p><b>代价</b>：每次回调最多 1 次会话主键查询 + 1 次 INSERT（恒为单条写入，无循环、无批量）。
 * 相对秒级的模型调用可忽略；用一次主键读换取用户维度的确定性归属。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@Component
@RequiredArgsConstructor
public class AdminAiUsageListener implements AiUsageListener {

    private static final Logger log = LoggerFactory.getLogger(AdminAiUsageListener.class);

    /** {@code ai_usage_log.error_message} 列宽；超长会直接报 SQL 错，故落库前显式截断 */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    private final AiChatSessionMapper sessionMapper;

    private final AiUsageLogWriter usageLogWriter;

    @Override
    public void onUsage(AiUsageInfo usage) {
        try {
            AiUsageLog entity = toEntity(usage);
            if (entity == null) {
                return;
            }
            usageLogWriter.write(entity);
        } catch (Exception ex) {
            // 落库失败绝不影响对话主流程（starter 亦会隔离），但必须留下完整堆栈与业务标识
            log.error("[ypbin-ai] AI 用量落库失败，本次用量未记录：conversationId={}, model={}, outcome={},"
                    + " promptTokens={}, generationTokens={}, totalTokens={}",
                usage.conversationId(), usage.model(), usage.outcome(), usage.promptTokens(),
                usage.generationTokens(), usage.totalTokens(), ex);
        }
    }

    /**
     * 把 starter 的用量事件转成落库实体。
     *
     * @param usage 用量事件
     * @return 待落库实体；租户无法确定或回调契约被破坏时返回 {@code null}（已记 error）
     */
    @Nullable
    private AiUsageLog toEntity(AiUsageInfo usage) {
        AiUsageOutcome outcome = usage.outcome();
        if (outcome == null) {
            // starter 契约保证非空；真出现说明契约被破坏，必须显式暴露而不是猜一个结果
            log.error("[ypbin-ai] 用量回调缺少终局结果（starter 契约违规），本次用量未记录：conversationId={}"
                + ", model={}, durationMs={}", usage.conversationId(), usage.model(), usage.durationMs());
            return null;
        }

        Long userId = IdentityContext.getUserId().orElse(null);
        // 先取显式绑定/Reactor 快照传播的租户，再回退身份头（同线程场景）；两者都不在才查会话行
        Long tenantId = TenantContext.getTenantId().or(UserContext::getTenantId).orElse(null);
        Long sessionId = parseSessionId(usage.conversationId());
        if (sessionId != null && (tenantId == null || userId == null)) {
            // 会话归属会话主键：忽略租户过滤按主键精确查一行（不是跨租户扫描）
            AiChatSession session = TenantContext.executeIgnore(() -> sessionMapper.selectById(sessionId));
            if (session != null) {
                if (tenantId == null) {
                    tenantId = session.getTenantId();
                }
                if (userId == null) {
                    userId = session.getUserId();
                }
            }
        }
        if (tenantId == null) {
            log.error("[ypbin-ai] 无法确定用量归属租户，本次用量未落库（禁止伪造成默认租户）："
                    + "conversationId={}, model={}, outcome={}, userId={}",
                usage.conversationId(), usage.model(), outcome.code(), userId);
            return null;
        }

        AiUsageLog entity = new AiUsageLog();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        // 非会话入口（share-<kbId> 等）没有会话实体，如实落 NULL，不伪造 ID
        entity.setConversationId(sessionId);
        entity.setModelName(usage.model());
        // token 为 NULL = 上游未回报，绝不折算成 0（0 会被当成真实用量参与计费/统计）
        entity.setInputTokens(toNullableInt(usage.promptTokens()));
        entity.setOutputTokens(toNullableInt(usage.generationTokens()));
        entity.setTotalTokens(toNullableInt(usage.totalTokens()));
        entity.setLatencyMs(usage.durationMs());
        entity.setOutcome(outcome.code());
        entity.setErrorMessage(truncate(usage.errorMessage()));
        return entity;
    }

    /**
     * 解析会话 ID：{@code conversationId} 是字符串（starter 契约），admin 会话主键是 BIGINT；
     * 匿名入口传入 {@code share-<kbId>} 等非数字标识，此时按「无会话」处理返回 {@code null}。
     */
    @Nullable
    private static Long parseSessionId(@Nullable String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return null;
        }
        for (int i = 0; i < conversationId.length(); i++) {
            if (!Character.isDigit(conversationId.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.valueOf(conversationId);
        } catch (NumberFormatException ex) {
            // 纯数字但超出 Long 范围：属异常数据，记 debug 并如实按无会话处理
            log.debug("[ypbin-ai] 会话标识超出 BIGINT 范围，按无会话落库：conversationId={}", conversationId, ex);
            return null;
        }
    }

    /**
     * token 值转换：{@code null} 保持 {@code null}（未知），非 null 时才转 {@code int}。
     * 单次调用 token 超过 {@code Integer.MAX_VALUE} 在物理上不可能，溢出按显式异常处理而非截断。
     */
    @Nullable
    private static Integer toNullableInt(@Nullable Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    /**
     * 失败原因截断到列宽（仅失败时有值；截断属列宽边界处理，不影响成功/失败判定）。
     */
    @Nullable
    private static String truncate(@Nullable String message) {
        if (message == null || message.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
