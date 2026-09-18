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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.admin.ai.entity.AiChatSession;
import cn.ypbin.admin.ai.entity.AiUsageLog;
import cn.ypbin.admin.ai.mapper.AiChatSessionMapper;
import cn.ypbin.admin.ai.mapper.AiUsageLogMapper;
import cn.ypbin.starter.ai.chat.usage.AiUsageInfo;
import cn.ypbin.starter.ai.chat.usage.AiUsageOutcome;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * AI 用量回调落库测试。
 *
 * <p>钉住四件事：① 成功/失败/取消三类终局各自落库并可区分；② token 为 {@code null}（上游未回报）
 * 必须原样落 NULL，<b>绝不折算成 0</b>（0 会被当成真实用量参与计费/统计）；③ 回调内部异常不逃逸到
 * 对话主流程，且必须带完整堆栈与业务标识记 error；④ 用户/租户维度由宿主补齐——回调线程上
 * {@code IdentityContext} 取不到用户时，用会话主键补齐；租户实在取不到则明确失败并留痕，
 * 绝不伪造成默认租户。</p>
 *
 * <p><b>测试边界</b>：本机无 MySQL/Reactor，落库以 Mockito 桩住 Mapper 验证（单条 INSERT 的参数）；
 * 「Reactor 回调线程上 ThreadLocal 到底有没有值」属运行期行为，未核实，故解析逻辑对
 * 「有上下文」与「无上下文需查会话」两条路径都做了显式断言。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class AdminAiUsageListenerTest {

    private static final Long SESSION_ID = 7L;

    private static final Long TENANT_ID = 5L;

    private static final Long USER_ID = 42L;

    private final AiChatSessionMapper sessionMapper = mock(AiChatSessionMapper.class);

    private final AiUsageLogMapper usageLogMapper = mock(AiUsageLogMapper.class);

    private final AdminAiUsageListener listener =
        new AdminAiUsageListener(sessionMapper, new AiUsageLogWriter(usageLogMapper));

    private ListAppender<ILoggingEvent> appender;

    private Logger listenerLogger;

    @BeforeEach
    void setUp() {
        listenerLogger = (Logger) LoggerFactory.getLogger(AdminAiUsageListener.class);
        appender = new ListAppender<>();
        appender.start();
        listenerLogger.addAppender(appender);
        // 用例之间不得残留上下文（ThreadLocal 会跨用例串味）
        TenantContext.clear();
        IdentityContext.clear();
    }

    @AfterEach
    void tearDown() {
        listenerLogger.detachAppender(appender);
        TenantContext.clear();
        IdentityContext.clear();
    }

    private static AiChatSession session(Long tenantId, Long userId) {
        AiChatSession session = new AiChatSession();
        session.setId(SESSION_ID);
        session.setTenantId(tenantId);
        session.setUserId(userId);
        return session;
    }

    private AiUsageLog capturePersisted() {
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("成功：落库并记 success，token 如实落库")
    void shouldPersistSuccess() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(TENANT_ID, USER_ID));

        listener.onUsage(AiUsageInfo.success("deepseek-v4", String.valueOf(SESSION_ID), 11L, 22L, 33L, 120L));

        AiUsageLog persisted = capturePersisted();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(USER_ID);
        assertThat(persisted.getConversationId()).isEqualTo(SESSION_ID);
        assertThat(persisted.getModelName()).isEqualTo("deepseek-v4");
        assertThat(persisted.getInputTokens()).isEqualTo(11);
        assertThat(persisted.getOutputTokens()).isEqualTo(22);
        assertThat(persisted.getTotalTokens()).isEqualTo(33);
        assertThat(persisted.getLatencyMs()).isEqualTo(120L);
        assertThat(persisted.getOutcome()).isEqualTo(AiUsageOutcome.SUCCESS.code());
        assertThat(persisted.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("失败：落库并记 failure + 失败原因（与成功可区分）")
    void shouldPersistFailure() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(TENANT_ID, USER_ID));

        listener.onUsage(AiUsageInfo.failure("deepseek-v4", String.valueOf(SESSION_ID), 5L, null, 5L, 900L,
            "upstream timeout"));

        AiUsageLog persisted = capturePersisted();
        assertThat(persisted.getOutcome()).isEqualTo(AiUsageOutcome.FAILURE.code());
        assertThat(persisted.getErrorMessage()).isEqualTo("upstream timeout");
        assertThat(persisted.getTotalTokens()).isEqualTo(5);
        // 生成 Token 上游未回报：必须为 NULL，不能写成 0
        assertThat(persisted.getOutputTokens()).isNull();
    }

    @Test
    @DisplayName("取消：落库并记 cancelled，不伪造失败原因")
    void shouldPersistCancelled() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(TENANT_ID, USER_ID));

        listener.onUsage(AiUsageInfo.cancelled("deepseek-v4", String.valueOf(SESSION_ID), null, null, null, 30L));

        AiUsageLog persisted = capturePersisted();
        assertThat(persisted.getOutcome()).isEqualTo(AiUsageOutcome.CANCELLED.code());
        assertThat(persisted.getErrorMessage()).isNull();
        assertThat(persisted.getInputTokens()).isNull();
        assertThat(persisted.getOutputTokens()).isNull();
        assertThat(persisted.getTotalTokens()).isNull();
    }

    @Test
    @DisplayName("token 为 null（上游未回报）绝不写成 0：三个 token 列必须全为 NULL 且不参与计费")
    void shouldNeverCoerceNullTokensToZero() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(TENANT_ID, USER_ID));

        listener.onUsage(AiUsageInfo.success("deepseek-v4", String.valueOf(SESSION_ID), null, null, null, 77L));

        AiUsageLog persisted = capturePersisted();
        assertThat(persisted.getInputTokens()).isNull();
        assertThat(persisted.getOutputTokens()).isNull();
        assertThat(persisted.getTotalTokens()).isNull();
    }

    @Test
    @DisplayName("落库失败：带完整堆栈记 error，且不向对话主流程抛异常")
    void shouldIsolatePersistFailure() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(TENANT_ID, USER_ID));
        when(usageLogMapper.insert(any(AiUsageLog.class)))
            .thenThrow(new IllegalStateException("insert failed"));

        assertThatCode(() -> listener.onUsage(
            AiUsageInfo.success("deepseek-v4", String.valueOf(SESSION_ID), 1L, 2L, 3L, 10L)))
            .doesNotThrowAnyException();

        assertThat(appender.list)
            .as("落库失败必须留痕（否则用量凭空消失且无人知道）")
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
                // 业务标识必须出现在日志里，否则无法定位是哪次调用丢了用量
                assertThat(event.getFormattedMessage()).contains(String.valueOf(SESSION_ID));
            });
    }

    @Test
    @DisplayName("租户取不到：明确失败 + error 留痕，绝不伪造成默认租户落库")
    void shouldRefusePersistWhenTenantUnknown() {
        // 匿名入口传入非会话标识，且线程上没有租户上下文 ⇒ 无法确定归属
        listener.onUsage(AiUsageInfo.success("deepseek-v4", "share-9", 1L, 2L, 3L, 10L));

        verify(usageLogMapper, never()).insert(any(AiUsageLog.class));
        assertThat(appender.list)
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).contains("无法确定用量归属租户");
            });
    }

    @Test
    @DisplayName("同线程上下文可用时不再查会话（省一次主键查询）")
    void shouldUseThreadLocalContextWithoutSessionLookup() {
        LoginUser loginUser = new LoginUser(USER_ID, "tester");
        loginUser.setTenantId(TENANT_ID);
        IdentityContext.setLoginUser(loginUser);

        listener.onUsage(AiUsageInfo.success("deepseek-v4", String.valueOf(SESSION_ID), 1L, 2L, 3L, 10L));

        verifyNoInteractions(sessionMapper);
        AiUsageLog persisted = capturePersisted();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("非会话入口（share-/widget- 前缀）：会话标识如实落 NULL、用户如实落 NULL，不伪造 ID")
    void shouldPersistAnonymousEntryWithoutFakeIds() {
        TenantContext.executeWithTenant(TENANT_ID, () -> {
            listener.onUsage(AiUsageInfo.success("deepseek-v4", "widget-9", 1L, 2L, 3L, 10L));
            return null;
        });

        verifyNoInteractions(sessionMapper);
        AiUsageLog persisted = capturePersisted();
        assertThat(persisted.getConversationId()).isNull();
        assertThat(persisted.getUserId()).isNull();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("落库写入在忽略租户过滤的作用域内执行并复位（租户归属由实体显式携带）")
    void shouldWriteInsideTenantIgnoreScopeAndRestore() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(TENANT_ID, USER_ID));
        AtomicBoolean ignoredDuringInsert = new AtomicBoolean(false);
        when(usageLogMapper.insert(any(AiUsageLog.class))).thenAnswer(invocation -> {
            ignoredDuringInsert.set(TenantContext.isIgnored());
            return 1;
        });

        listener.onUsage(AiUsageInfo.success("deepseek-v4", String.valueOf(SESSION_ID), 1L, 2L, 3L, 10L));

        assertThat(ignoredDuringInsert).isTrue();
        assertThat(TenantContext.isIgnored()).isFalse();
        verify(usageLogMapper, times(1)).insert(any(AiUsageLog.class));
    }

    @Test
    @DisplayName("超长失败原因按列宽截断（否则 INSERT 直接报 SQL 错，用量丢失）")
    void shouldTruncateOverlongErrorMessage() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(TENANT_ID, USER_ID));
        String overlong = "x".repeat(600);

        listener.onUsage(AiUsageInfo.failure("deepseek-v4", String.valueOf(SESSION_ID), null, null, null, 10L,
            overlong));

        assertThat(capturePersisted().getErrorMessage()).hasSize(500);
    }
}
