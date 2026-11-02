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

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.ai.core.AiChatUserContext;
import cn.ypbin.admin.ai.core.AiChatUserThreadLocalAccessor;
import cn.ypbin.admin.ai.entity.AiChatMessage;
import cn.ypbin.admin.ai.entity.AiChatRole;
import cn.ypbin.admin.ai.entity.AiChatSession;
import cn.ypbin.admin.ai.mapper.AiChatMessageMapper;
import cn.ypbin.admin.ai.mapper.AiChatRoleMapper;
import cn.ypbin.admin.ai.mapper.AiChatSessionMapper;
import cn.ypbin.admin.ai.model.req.AiChatSendReq;
import cn.ypbin.admin.ai.model.req.AiChatSessionCreateReq;
import cn.ypbin.admin.ai.model.resp.AiChatMessageResp;
import cn.ypbin.admin.ai.model.resp.AiChatSessionResp;
import cn.ypbin.admin.ai.service.AiChatSessionService;
import cn.ypbin.admin.ai.support.AiChatSseSupport;
import cn.ypbin.starter.messaging.sse.SseStreamBridge;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import cn.ypbin.starter.tenant.core.TenantThreadLocalAccessor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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

    /** 重新生成时同步等待上限：远程模型无响应时及时失败，避免请求线程无限阻塞 */
    private static final Duration REGENERATE_BLOCK_TIMEOUT = Duration.ofSeconds(120);

    /** 单轮对话落库消息数（用户消息 + 助手回复） */
    private static final int MESSAGES_PER_TURN = 2;

    /** 内置角色标记（{@code ai_chat_role.is_builtin=1}，tenant_id=0） */
    private static final int BUILTIN_YES = 1;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiChatRoleMapper roleMapper;
    private final AiKnowledgeCrudComponent knowledgeCrudComponent;

    /** starter AI 对话服务（可选注入：AI 未启用时不影响服务启动） */
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    /**
     * 编程式事务：流式接口内新建会话时，{@code this.createSession()} 是自调用，
     * 走不到 Spring 的 {@code @Transactional} 代理，必须显式套一层事务。
     */
    private final TransactionTemplate transactionTemplate;

    public AiChatServiceImpl(AiChatSessionMapper sessionMapper,
                             AiChatMessageMapper messageMapper,
                             AiChatRoleMapper roleMapper,
                             AiKnowledgeCrudComponent knowledgeCrudComponent,
                             ObjectProvider<AiChatService> aiChatServiceProvider,
                             PlatformTransactionManager transactionManager) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.roleMapper = roleMapper;
        this.knowledgeCrudComponent = knowledgeCrudComponent;
        this.aiChatServiceProvider = aiChatServiceProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<AiChatSessionResp> listSessions() {
        Long userId = requireUserId();
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
            : roleMapper.selectByIds(roleIds).stream()
                .collect(Collectors.toMap(AiChatRole::getId, role -> role));
        return sessions.stream().map(session -> toSessionResp(session, roleById)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSession(AiChatSessionCreateReq req) {
        Long userId = requireUserId();
        Long tenantId = UserContext.getTenantId().orElseThrow(
            () -> new BusinessException("无法获取当前租户上下文"));
        if (req.getRoleId() != null) {
            // 入库前校验角色可见性（内置角色 tenant_id=0 或本租户自定义角色），
            // 防止会话里存下其他租户的 roleId，后续解析人设时越权
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
        if (sessionMapper.insert(session) != 1) {
            throw new BusinessException("会话创建失败");
        }
        return session.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        requireSession(sessionId);
        if (sessionMapper.deleteById(sessionId) != 1) {
            throw new BusinessException("会话删除失败");
        }
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
    @Transactional(rollbackFor = Exception.class)
    public AiChatMessageResp sendMessage(AiChatSendReq req) {
        throw new BusinessException("请使用流式接口 /ai/chat/stream 发送消息");
    }

    @Override
    public SseEmitter sendMessageStream(AiChatSendReq req) {
        Long sessionId = req.getSessionId();
        // 无 sessionId：先创建会话（携带角色），使会话成为唯一载体
        if (sessionId == null) {
            AiChatSessionCreateReq createReq = new AiChatSessionCreateReq();
            createReq.setRoleId(req.getRoleId());
            createReq.setModelId(req.getModelId());
            // 显式事务：createSession 的 @Transactional 在自调用下不生效，必须由 TransactionTemplate 兜底
            sessionId = transactionTemplate.execute(status -> createSession(createReq));
        }
        final Long finalSessionId = sessionId;
        AiChatSession session = requireSession(finalSessionId);
        Long userId = requireUserId();
        Long tenantId = UserContext.getTenantId().orElseThrow(
            () -> new BusinessException("无法获取当前租户上下文"));

        // AI 未启用：推送错误帧
        AiChatService aiSvc = aiChatServiceProvider.getIfAvailable();
        if (aiSvc == null) {
            return errorEmitter("AI 模块未启用，请配置 ypbin.ai.enabled=true");
        }

        // RAG 分支：进入检索前校验知识库归属（防跨租户知识库内容泄露），失败时提前拒绝、不落用户消息
        if (req.getKnowledgeBaseId() != null) {
            knowledgeCrudComponent.requireKb(req.getKnowledgeBaseId());
        }

        // 落库用户消息
        insertMessage(finalSessionId, userId, tenantId, ROLE_USER, req.getContent(), null);

        // 构造对话流：优先 RAG > 角色人设 > 默认
        String convId = String.valueOf(finalSessionId);
        String rolePrompt = resolveRoleSystemPrompt(session);
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

        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        // contextWrite 将 tenantId 与登录用户写入 Reactor Context；
        // Hooks.enableAutomaticContextPropagation() + 两个 ThreadLocalAccessor 在每次
        // Scheduler 线程切换时从 Context 取出快照并还原 ThreadLocal，无时序依赖。
        // 用户上下文是管理端工具的准入门槛：公开分享/挂件复用同一批 @Tool，
        // 不带该标记时工具拒绝执行（见 AiChatUserContext）。
        Disposable subscription = stream
            .contextWrite(ctx -> ctx.put(
                    TenantThreadLocalAccessor.KEY,
                    new TenantContext.ContextSnapshot(null, tenantId))
                .put(AiChatUserThreadLocalAccessor.KEY,
                    new AiChatUserContext.Snapshot(userId)))
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
                    assistantContent, null);
                AiChatSession updatedSession = updateSessionStats(
                    finalSessionId, tokens, MESSAGES_PER_TURN);
                // 首条消息自动生成标题（以「标题仍为默认值」判定是否首次）；
                // 复用上面刚查出的会话，避免同一次回调里对同一行再查一遍
                autoTitleIfNew(updatedSession, req.getContent());
            })
            .subscribe();
        subscriptionRef.set(subscription);
        emitter.onTimeout(() -> disposeQuietly(subscriptionRef));
        emitter.onError(e -> disposeQuietly(subscriptionRef));
        return emitter;
    }

    /**
     * 重新生成最后一条回答。
     *
     * <p><strong>为什么方法上不再标 {@code @Transactional}</strong>：远程模型调用最长等待
     * {@link #REGENERATE_BLOCK_TIMEOUT}，把它包在事务里会长时间占用数据库连接与行锁
     * （并发几次即可耗尽连接池）。现在只把「读消息」与「删旧答 + 插新答」放在事务里，
     * 远程调用在事务外执行；模型调用失败时旧回答仍然保留，与原先的回滚语义一致。</p>
     */
    @Override
    public AiChatMessageResp regenerateLastMessage(Long sessionId) {
        AiChatSession session = requireSession(sessionId);
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
        final Long userId = requireUserId();
        final Long tenantId = UserContext.getTenantId().orElseThrow(
            () -> new BusinessException("无法获取当前租户上下文"));
        String convId = String.valueOf(sessionId);
        String rolePrompt = resolveRoleSystemPrompt(session);
        final String input = lastUserContent;
        // 阻塞式调用，没有 contextWrite 可用：把「租户 + 登录用户」写进 ThreadLocal，
        // 由 context-propagation 在订阅时捕获并还原到工具执行线程
        String reply = runInChatContext(userId, tenantId, () -> rolePrompt != null
            ? aiSvc.chatWithSystemPrompt(convId, rolePrompt, input)
                .collectList().blockOptional(REGENERATE_BLOCK_TIMEOUT).orElse(List.of())
                .stream().collect(Collectors.joining())
            : aiSvc.chat(convId, input));

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
        if (sessionMapper.updateById(session) != 1) {
            throw new BusinessException("会话标题更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleSessionPin(Long sessionId) {
        AiChatSession session = requireSession(sessionId);
        session.setIsPinned(session.getIsPinned() == 1 ? 0 : 1);
        if (sessionMapper.updateById(session) != 1) {
            throw new BusinessException("会话置顶状态更新失败");
        }
    }

    // ========== 内部辅助 ==========

    private AiChatSession requireSession(Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        Long userId = requireUserId();
        // Objects.equals：会话 userId 为空（历史脏数据）时不能抛 NPE
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException("无权访问该会话");
        }
        return session;
    }

    private Long requireUserId() {
        return IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    /**
     * 在「登录用户 + 租户」上下文中执行阻塞式 AI 调用（重新生成链路）。
     *
     * @param userId   登录用户 ID（管理端工具的准入门槛）
     * @param tenantId 租户 ID
     * @param call     AI 调用
     * @return 回答文本
     */
    private static String runInChatContext(Long userId, Long tenantId, Supplier<String> call) {
        return AiChatUserContext.executeWithUser(userId,
            () -> TenantContext.executeWithTenant(tenantId, call));
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
        if (messageMapper.insert(msg) != 1) {
            throw new BusinessException("消息保存失败");
        }
    }

    /**
     * 累加会话统计并返回本次读取的会话（供同回调内的标题更新复用，避免同一行查两遍）。
     *
     * @param sessionId     会话 ID
     * @param addedTokens   本次新增 token 数
     * @param addedMessages 本次新增消息数
     * @return 更新后的会话；会话不存在时返回 {@code null}
     */
    private AiChatSession updateSessionStats(Long sessionId, int addedTokens, int addedMessages) {
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
            return null;
        }
        // 回读一次供同回调内的标题更新复用（总查询次数与原实现相同：一次写 + 一次读）
        return TenantContext.executeIgnore(() -> sessionMapper.selectById(sessionId));
    }

    /**
     * 新会话首条消息后自动截取标题（前 {@link #TITLE_MAX_LENGTH} 字），避免长期"新对话"。
     *
     * @param session      已加载的会话（{@code null} 表示会话不存在）
     * @param firstUserMsg 首条用户消息
     */
    private void autoTitleIfNew(AiChatSession session, String firstUserMsg) {
        if (session == null || !DEFAULT_TITLE.equals(session.getTitle())) {
            return;
        }
        String title = firstUserMsg == null || firstUserMsg.isBlank() ? DEFAULT_TITLE
            : (firstUserMsg.length() > TITLE_MAX_LENGTH
                ? firstUserMsg.substring(0, TITLE_MAX_LENGTH) + "…" : firstUserMsg);
        session.setTitle(title);
        int affected = TenantContext.executeIgnore(() -> sessionMapper.updateById(session));
        if (affected != 1) {
            log.error("[ypbin-ai] 会话标题自动更新失败：sessionId={}", session.getId());
        }
    }

    /**
     * 取会话角色的人设提示词。
     *
     * <p>入参直接收已加载的会话：调用方（流式/重新生成）刚刚 {@code requireSession} 过，
     * 再按 ID 查一次就是同一条 SQL 白跑两遍。</p>
     *
     * @param session 已通过归属校验的会话
     * @return 系统提示词；未配置角色时返回 null
     */
    private String resolveRoleSystemPrompt(AiChatSession session) {
        if (session.getRoleId() == null) {
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
                session.getId(), session.getRoleId());
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

    /**
     * 构造「只带错误帧」的 SSE 响应：交给 starter 的流式桥接（通用传输能力，不在业务侧重复实现）。
     *
     * <p>starter 版本带参数校验（{@code Objects.requireNonNull}）与可定制选项，语义与本仓原副本一致
     * （立即发 {@code event:error} 帧后 complete），故切换到 starter 不改变前端行为。</p>
     *
     * @param message 错误文案（原样发出）
     * @return 已完成的 SSE 响应
     */
    private static SseEmitter errorEmitter(String message) {
        return SseStreamBridge.errorEmitter(message);
    }

    /**
     * 释放流式订阅（幂等）。
     *
     * <p>保留在业务侧的原因见 {@link AiChatSseSupport} 类注释（starter 用 {@code Disposable.Swap}，
     * 与本仓 {@code AtomicReference<Disposable>} 类型不同）。</p>
     *
     * @param ref 订阅引用
     */
    private static void disposeQuietly(AtomicReference<Disposable> ref) {
        AiChatSseSupport.disposeQuietly(ref);
    }

    /**
     * 取异常链最深层原因的消息文本（交给 starter，避免与基座重复实现并随其漂移）。
     *
     * @param e 异常
     * @return 根因文案（非空）
     */
    private static String rootMessage(Throwable e) {
        return SseStreamBridge.rootMessage(e);
    }
}
