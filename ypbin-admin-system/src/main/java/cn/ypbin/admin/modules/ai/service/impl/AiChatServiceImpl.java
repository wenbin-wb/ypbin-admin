/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.service.impl;

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.modules.ai.entity.AiChatMessage;
import cn.ypbin.admin.modules.ai.entity.AiChatRole;
import cn.ypbin.admin.modules.ai.entity.AiChatSession;
import cn.ypbin.admin.modules.ai.mapper.AiChatMessageMapper;
import cn.ypbin.admin.modules.ai.mapper.AiChatRoleMapper;
import cn.ypbin.admin.modules.ai.mapper.AiChatSessionMapper;
import cn.ypbin.admin.modules.ai.model.req.AiChatSendReq;
import cn.ypbin.admin.modules.ai.model.req.AiChatSessionCreateReq;
import cn.ypbin.admin.modules.ai.model.resp.AiChatMessageResp;
import cn.ypbin.admin.modules.ai.model.resp.AiChatSessionResp;
import cn.ypbin.admin.modules.ai.service.AiChatSessionService;
import cn.ypbin.admin.modules.ai.support.AiChatSseSupport;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import cn.ypbin.starter.tenant.core.TenantThreadLocalAccessor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * AI 会话服务实现。
 *
 * <p>以 {@code ai_chat_session} 为唯一会话载体，负责会话/消息 CRUD；流式对话直接基于
 * starter 的 {@link AiChatService}（以 sessionId 作为 conversationId 维护记忆），
 * 消息落库到 {@code ai_chat_message}。无 conversation 表冗余，架构统一。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Service
public class AiChatServiceImpl implements AiChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    /** 消息角色：用户 */
    private static final String ROLE_USER = "user";

    /** 消息角色：助手 */
    private static final String ROLE_ASSISTANT = "assistant";

    /** 新会话默认标题 */
    private static final String DEFAULT_TITLE = "新对话";

    /** 自动截取标题的最大长度（字符） */
    private static final int TITLE_MAX_LENGTH = 50;

    /** 单轮对话落库消息数（用户消息 + 助手回复） */
    private static final int MESSAGES_PER_TURN = 2;

    /** 内置角色标记（{@code ai_chat_role.is_builtin=1}，tenant_id=0） */
    private static final int BUILTIN_YES = 1;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiChatRoleMapper roleMapper;

    /** starter AI 对话服务（可选注入：AI 未启用时不影响服务启动） */
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    /** 短事务模板：把远程模型调用隔在事务外，避免长时间占用数据库连接 */
    private final TransactionTemplate transactionTemplate;

    public AiChatServiceImpl(AiChatSessionMapper sessionMapper,
                             AiChatMessageMapper messageMapper,
                             AiChatRoleMapper roleMapper,
                             ObjectProvider<AiChatService> aiChatServiceProvider,
                             PlatformTransactionManager transactionManager) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.roleMapper = roleMapper;
        this.aiChatServiceProvider = aiChatServiceProvider;
        // Spring Boot 只自动装配 PlatformTransactionManager，不提供 TransactionTemplate Bean
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<AiChatSessionResp> listSessions() {
        Long userId = LoginHelper.getUserId();
        List<AiChatSession> sessions = sessionMapper.selectList(
            new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getStatus, EntityStatus.ENABLED.getCode())
                .orderByDesc(AiChatSession::getIsPinned)
                .orderByDesc(AiChatSession::getLastMessageAt));
        if (sessions.isEmpty()) {
            return List.of();
        }
        // 批量预取角色，避免逐个会话查角色表（N+1）
        Set<Long> roleIds = sessions.stream()
            .map(AiChatSession::getRoleId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, AiChatRole> roleById = roleIds.isEmpty() ? Map.of()
            : roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(AiChatRole::getId, role -> role));
        return sessions.stream().map(session -> toSessionResp(session, roleById)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSession(AiChatSessionCreateReq req) {
        Long userId = LoginHelper.getUserId();
        Long tenantId = UserContext.getTenantId().orElseThrow(
            () -> new BusinessException("无法获取当前租户上下文"));
        if (req.getRoleId() != null) {
            // 入库前校验角色可见性（内置角色 tenant_id=0 或本租户自定义角色）
            requireVisibleRole(req.getRoleId());
        }
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTenantId(tenantId);
        session.setTitle(req.getTitle() != null ? req.getTitle() : DEFAULT_TITLE);
        session.setRoleId(req.getRoleId());
        session.setModelId(req.getModelId());
        session.setContextWindow(10);
        session.setTotalTokens(0);
        session.setMessageCount(0);
        session.setIsPinned(0);
        session.setStatus(EntityStatus.ENABLED.getCode());
        sessionMapper.insert(session);
        return session.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        requireSession(sessionId);
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>()
            .eq(AiChatMessage::getSessionId, sessionId));
        // 清除 Spring AI 记忆
        AiChatService svc = aiChatServiceProvider.getIfAvailable();
        if (svc != null) {
            svc.clearMemory(String.valueOf(sessionId));
        }
    }

    @Override
    public List<AiChatMessageResp> listMessages(Long sessionId) {
        requireSession(sessionId);
        List<AiChatMessage> messages = messageMapper.selectList(
            new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime));
        return messages.stream().map(this::toMessageResp).toList();
    }

    @Override
    public SseEmitter sendMessageStream(AiChatSendReq req) {
        Long sessionId = req.getSessionId();
        // 无 sessionId：先创建会话（携带角色），使会话成为唯一载体
        if (sessionId == null) {
            AiChatSessionCreateReq createReq = new AiChatSessionCreateReq();
            createReq.setRoleId(req.getRoleId());
            createReq.setModelId(req.getModelId());
            sessionId = createSession(createReq);
        }
        final Long finalSessionId = sessionId;
        requireSession(finalSessionId);
        Long userId = LoginHelper.getUserId();
        Long tenantId = UserContext.getTenantId().orElseThrow(
            () -> new BusinessException("无法获取当前租户上下文"));

        // AI 未启用：推送错误帧
        AiChatService aiSvc = aiChatServiceProvider.getIfAvailable();
        if (aiSvc == null) {
            return errorEmitter("AI 模块未启用，请配置 ypbin.ai.enabled=true");
        }

        // 落库用户消息
        insertMessage(finalSessionId, userId, tenantId, ROLE_USER, req.getContent(), null);

        // 构造对话流：优先 RAG > 角色人设 > 默认
        String convId = String.valueOf(finalSessionId);
        String rolePrompt = resolveRoleSystemPrompt(finalSessionId);
        Flux<String> stream;
        if (req.getKnowledgeBaseId() != null) {
            // RAG 检索增强：基于指定知识库回答
            stream = aiSvc.chatWithKnowledge(convId, req.getContent(),
                String.valueOf(req.getKnowledgeBaseId()));
        } else if (rolePrompt != null) {
            stream = aiSvc.chatWithSystemPrompt(convId, rolePrompt, req.getContent());
        } else {
            stream = aiSvc.chatStream(convId, req.getContent());
        }

        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<StringBuilder> contentBuffer = new AtomicReference<>(new StringBuilder());
        AtomicInteger tokenCount = new AtomicInteger(0);
        AtomicInteger messageCount = new AtomicInteger(0);

        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        // contextWrite 将 tenantId 写入 Reactor Context；
        // Hooks.enableAutomaticContextPropagation() + TenantThreadLocalAccessor 在每次
        // Scheduler 线程切换时从 Context 取出快照并还原 ThreadLocal，无时序依赖。
        Disposable subscription = stream
            .contextWrite(ctx -> ctx.put(
                TenantThreadLocalAccessor.KEY,
                new TenantContext.ContextSnapshot(null, tenantId)))
            .doOnNext(token -> {
                try {
                    emitter.send(token);
                    contentBuffer.get().append(token);
                    tokenCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("[ypbin-ai] 发送流式帧失败：sessionId={}", finalSessionId, e);
                    disposeQuietly(subscriptionRef);
                    emitter.complete();
                }
            })
            .doOnError(e -> {
                log.error("[ypbin-ai] 流式对话失败：sessionId={}", finalSessionId, e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                        .data("对话出错：" + rootMessage(e)));
                } catch (Exception sendEx) {
                    log.warn("[ypbin-ai] 发送错误提示失败", sendEx);
                }
                disposeQuietly(subscriptionRef);
                emitter.complete();
            })
            .doOnComplete(() -> {
                emitter.complete();
                // 落库助手回复 + 更新会话统计
                String assistantContent = contentBuffer.get().toString();
                int tokens = tokenCount.get();
                insertMessage(finalSessionId, userId, tenantId, ROLE_ASSISTANT,
                    assistantContent, modelName());
                updateSessionStats(finalSessionId, tokens, MESSAGES_PER_TURN);
                // 首条消息自动生成标题
                if (messageCount.incrementAndGet() == 1) {
                    autoTitleIfNew(finalSessionId, req.getContent());
                }
            })
            // 必须提供 errorConsumer：doOnError 只是副作用算子，错误仍会传播到
            // subscribe() 的 onErrorDropped（打印一条无上下文的 Reactor 错误）
            .subscribe(token -> { }, error -> { });
        subscriptionRef.set(subscription);
        emitter.onTimeout(() -> disposeQuietly(subscriptionRef));
        emitter.onError(e -> disposeQuietly(subscriptionRef));
        // 客户端正常断开/完成时同样释放上游订阅
        emitter.onCompletion(() -> disposeQuietly(subscriptionRef));
        return emitter;
    }

    /**
     * 重新生成最后一条回答。
     *
     * <p><strong>为什么方法上不再标 {@code @Transactional}</strong>：远程模型调用最长可能等待
     * 很久，把它包在事务里会长时间占用数据库连接与行锁。现在只把「读消息」与
     * 「删旧答 + 插新答」放在事务里，远程调用在事务外执行；模型调用失败时旧回答仍然保留。</p>
     */
    @Override
    public AiChatMessageResp regenerateLastMessage(Long sessionId) {
        requireSession(sessionId);
        AiChatService aiSvc = aiChatServiceProvider.getIfAvailable();
        if (aiSvc == null) {
            throw new BusinessException("AI 模块未启用，请配置 ypbin.ai.enabled=true");
        }
        List<AiChatMessage> messages = messageMapper.selectList(
            new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime));
        if (messages.isEmpty()) {
            throw new BusinessException("会话暂无消息，无法重新生成");
        }
        // 取最后一条助手回复（待替换）与最后一条用户消息作为重生成输入
        String lastUserContent = null;
        AiChatMessage lastAssistant = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessage msg = messages.get(i);
            if (ROLE_ASSISTANT.equals(msg.getRole()) && lastAssistant == null) {
                lastAssistant = msg;
                continue;
            }
            if (ROLE_USER.equals(msg.getRole())) {
                lastUserContent = msg.getContent();
                break;
            }
        }
        if (lastAssistant == null || lastUserContent == null) {
            throw new BusinessException("缺少可重新生成的对话消息");
        }
        final AiChatMessage assistantToReplace = lastAssistant;
        final Long userId = LoginHelper.getUserId();
        final Long tenantId = UserContext.getTenantId().orElseThrow(
            () -> new BusinessException("无法获取当前租户上下文"));
        String convId = String.valueOf(sessionId);
        String rolePrompt = resolveRoleSystemPrompt(sessionId);
        final String input = lastUserContent;
        String reply;
        if (rolePrompt != null) {
            reply = aiSvc.chatWithSystemPrompt(convId, rolePrompt, input)
                .collectList().blockOptional().orElse(List.of())
                .stream().collect(Collectors.joining());
        } else {
            reply = aiSvc.chat(convId, input);
        }
        // 远程调用成功后再进短事务：删旧答 + 插新答要么同时成功、要么同时回滚
        AiChatMessage saved = transactionTemplate.execute(status -> {
            if (messageMapper.deleteById(assistantToReplace.getId()) != 1) {
                throw new BusinessException("原回答已变化，请刷新后重试");
            }
            AiChatMessage newReply = new AiChatMessage();
            newReply.setSessionId(sessionId);
            newReply.setUserId(userId);
            newReply.setTenantId(tenantId);
            newReply.setRole(ROLE_ASSISTANT);
            newReply.setContent(reply);
            newReply.setModelName(modelName());
            newReply.setCreateTime(LocalDateTime.now());
            if (messageMapper.insert(newReply) != 1) {
                throw new BusinessException("消息保存失败");
            }
            return newReply;
        });
        return toMessageResp(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionTitle(Long sessionId, String title) {
        AiChatSession session = requireSession(sessionId);
        session.setTitle(title);
        sessionMapper.updateById(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleSessionPin(Long sessionId) {
        AiChatSession session = requireSession(sessionId);
        session.setIsPinned(session.getIsPinned() == 1 ? 0 : 1);
        sessionMapper.updateById(session);
    }

    // ========== 内部辅助 ==========

    private AiChatSession requireSession(Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        Long userId = LoginHelper.getUserId();
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该会话");
        }
        return session;
    }

    private void insertMessage(Long sessionId, Long userId, Long tenantId,
            String role, String content, String modelName) {
        AiChatMessage msg = new AiChatMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setTenantId(tenantId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setModelName(modelName);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private void updateSessionStats(Long sessionId, int addedTokens, int addedMessages) {
        // 原子累加：先读后加再写在同一会话并发流式时会丢更新（两个回调覆盖同一行）。
        // doOnComplete 运行在 Reactor 线程，无租户上下文；忽略租户拦截。
        int affected = TenantContext.executeIgnore(() -> sessionMapper.update(null,
            new LambdaUpdateWrapper<AiChatSession>()
                .eq(AiChatSession::getId, sessionId)
                .setSql("total_tokens = COALESCE(total_tokens, 0) + " + addedTokens)
                .setSql("message_count = COALESCE(message_count, 0) + " + addedMessages)
                .set(AiChatSession::getLastMessageAt, LocalDateTime.now())));
        if (affected != 1) {
            log.error("[ypbin-ai] 会话统计更新失败：sessionId={}", sessionId);
        }
    }

    /** 新会话首条消息后自动截取标题（前 {@link #TITLE_MAX_LENGTH} 字），避免长期"新对话" */
    private void autoTitleIfNew(Long sessionId, String firstUserMsg) {
        AiChatSession session = TenantContext.executeIgnore(() -> sessionMapper.selectById(sessionId));
        if (session == null || !DEFAULT_TITLE.equals(session.getTitle())) {
            return;
        }
        String title = firstUserMsg == null || firstUserMsg.isBlank() ? DEFAULT_TITLE
            : (firstUserMsg.length() > TITLE_MAX_LENGTH
                ? firstUserMsg.substring(0, TITLE_MAX_LENGTH) + "…" : firstUserMsg);
        session.setTitle(title);
        TenantContext.executeIgnore(() -> {
            sessionMapper.updateById(session);
            return null;
        });
    }

    /** 当前使用的模型名（简化：暂无模型信息回传，留空即可） */
    private String modelName() {
        return null;
    }

    private String resolveRoleSystemPrompt(Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getRoleId() == null) {
            return null;
        }
        // 内置角色 tenant_id=0，普通 selectById 会被租户插件过滤掉 → 人设静默不生效
        AiChatRole role = TenantContext.executeIgnore(() -> roleMapper.selectById(session.getRoleId()));
        if (role == null) {
            return null;
        }
        if (!isVisibleRole(role)) {
            // 历史脏数据可能存了其他租户的 roleId：不回退成默认助手，也不泄露对方人设，留痕暴露
            log.warn("[ypbin-ai] 会话角色不可见，忽略该角色：sessionId={}, roleId={}",
                sessionId, session.getRoleId());
            return null;
        }
        if (role.getSystemPrompt() == null || role.getSystemPrompt().isBlank()) {
            return null;
        }
        return role.getSystemPrompt();
    }

    /**
     * 取角色并校验可见性（内置角色或当前租户自定义角色）。
     *
     * @param roleId 角色 ID
     * @return 角色实体
     */
    private AiChatRole requireVisibleRole(Long roleId) {
        AiChatRole role = TenantContext.executeIgnore(() -> roleMapper.selectById(roleId));
        if (role == null || !isVisibleRole(role)) {
            throw new BusinessException("角色不存在或无权使用");
        }
        return role;
    }

    /**
     * 角色是否对当前租户可见：内置角色全局可见，自定义角色仅所属租户可见。
     *
     * @param role 角色
     * @return 是否可见
     */
    private boolean isVisibleRole(AiChatRole role) {
        if (role.getIsBuiltin() != null && role.getIsBuiltin() == BUILTIN_YES) {
            return true;
        }
        Long tenantId = UserContext.getTenantId().orElse(null);
        return tenantId != null && tenantId.equals(role.getTenantId());
    }

    private AiChatSessionResp toSessionResp(AiChatSession session) {
        return toSessionResp(session, Map.of());
    }

    private AiChatSessionResp toSessionResp(AiChatSession session, Map<Long, AiChatRole> roleById) {
        AiChatSessionResp resp = new AiChatSessionResp();
        BeanUtils.copyProperties(session, resp);
        if (session.getRoleId() != null) {
            AiChatRole role = roleById.get(session.getRoleId());
            if (role != null) {
                resp.setRoleName(role.getName());
                resp.setRoleAvatar(role.getAvatar());
            }
        }
        return resp;
    }

    private AiChatMessageResp toMessageResp(AiChatMessage message) {
        AiChatMessageResp resp = new AiChatMessageResp();
        BeanUtils.copyProperties(message, resp);
        if (message.getImages() != null && !message.getImages().isBlank()) {
            resp.setImages(List.of(message.getImages().split(",")));
        }
        return resp;
    }

    private static SseEmitter errorEmitter(String message) {
        return AiChatSseSupport.errorEmitter(message);
    }

    private static void disposeQuietly(AtomicReference<Disposable> ref) {
        AiChatSseSupport.disposeQuietly(ref);
    }

    private static String rootMessage(Throwable e) {
        return AiChatSseSupport.rootMessage(e);
    }
}
