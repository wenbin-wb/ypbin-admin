/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.ai.service.impl;

import cn.ypbin.admin.system.ai.entity.AiConversation;
import cn.ypbin.admin.system.ai.entity.AiMessage;
import cn.ypbin.admin.system.ai.entity.AiModelConfig;
import cn.ypbin.admin.system.ai.entity.AiUsageLog;
import cn.ypbin.admin.system.ai.mapper.AiConversationMapper;
import cn.ypbin.admin.system.ai.mapper.AiMessageMapper;
import cn.ypbin.admin.system.ai.mapper.AiModelConfigMapper;
import cn.ypbin.admin.system.ai.mapper.AiPromptTemplateMapper;
import cn.ypbin.admin.system.ai.mapper.AiUsageLogMapper;
import cn.ypbin.admin.system.ai.model.resp.AiConversationResp;
import cn.ypbin.admin.system.ai.model.resp.AiMessageResp;
import cn.ypbin.admin.system.ai.service.AiChatBizService;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * AI 对话业务实现。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiChatBizServiceImpl implements AiChatBizService {

    private static final Logger log = LoggerFactory.getLogger(AiChatBizServiceImpl.class);

    /** 可选注入：未配置模型时 AI 功能优雅降级，不影响服务启动 */
    private final ObjectProvider<AiChatService> aiChatServiceProvider;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiPromptTemplateMapper promptTemplateMapper;
    private final AiUsageLogMapper usageLogMapper;
    private final AiModelConfigMapper modelConfigMapper;

    @Override
    public SseEmitter chat(Long conversationId, String message, Long knowledgeBaseId,
            Long promptTemplateId) {
        Flux<String> stream = buildChatStream(conversationId, message, knowledgeBaseId,
            promptTemplateId, null);
        return streamConversation(conversationId, message, stream);
    }

    @Override
    public SseEmitter chatWithRole(Long conversationId, String message, String roleSystemPrompt) {
        Flux<String> stream = buildChatStream(conversationId, message, null, null,
            roleSystemPrompt);
        return streamConversation(conversationId, message, stream);
    }

    /**
     * 构造对话 Reactor 流：优先 RAG、其次角色系统提示词、再 Prompt 模板、最后默认对话。
     */
    private Flux<String> buildChatStream(Long conversationId, String message,
            Long knowledgeBaseId, Long promptTemplateId, String roleSystemPrompt) {
        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService == null) {
            return Flux.empty();
        }
        String convIdStr = String.valueOf(conversationId);
        if (knowledgeBaseId != null) {
            return aiChatService.chatWithKnowledge(convIdStr, message,
                String.valueOf(knowledgeBaseId));
        }
        if (roleSystemPrompt != null) {
            return aiChatService.chatWithSystemPrompt(convIdStr, roleSystemPrompt, message);
        }
        if (promptTemplateId != null) {
            return aiChatService.chatWithSystemPrompt(convIdStr,
                resolveSystemPrompt(promptTemplateId), message);
        }
        return aiChatService.chatStream(convIdStr, message);
    }

    /**
     * 将对话流订阅到 SSE：落库用户消息、自动生成标题、流式推送、异步保存助手回复。
     */
    private SseEmitter streamConversation(Long conversationId, String message,
            Flux<String> stream) {
        Long userId = LoginHelper.getUserId();
        Long tenantId = currentTenantId();

        Long finalConvId = ensureConversation(conversationId, userId, tenantId);
        saveMessage(finalConvId, tenantId, "user", message, 0);

        if (conversationId == null) {
            String title = message.length() > 50 ? message.substring(0, 50) + "…" : message;
            AiConversation conv = new AiConversation();
            conv.setId(finalConvId);
            conv.setTitle(title);
            conversationMapper.updateById(conv);
        }

        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<StringBuilder> contentBuffer = new AtomicReference<>(new StringBuilder());
        AtomicInteger tokenCount = new AtomicInteger(0);

        if (aiChatServiceProvider.getIfAvailable() == null) {
            try {
                emitter.send(SseEmitter.event().name("error")
                    .data("AI 模块未启用，请在配置中设置 ypbin.ai.enabled=true 并引入模型 starter"));
            } catch (Exception e) {
                log.warn("[ypbin-ai] 发送错误提示失败", e);
            }
            emitter.complete();
            return emitter;
        }

        String convIdStr = String.valueOf(finalConvId);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        Disposable subscription = stream
            .doOnNext(token -> {
                try {
                    emitter.send(token);
                    contentBuffer.get().append(token);
                    tokenCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("[ypbin-ai] 发送流式帧失败：conversationId={}", finalConvId, e);
                    disposeQuietly(subscriptionRef);
                    emitter.complete();
                }
            })
            .doOnError(e -> {
                log.error("[ypbin-ai] 流式对话失败：conversationId={}", finalConvId, e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                        .data("对话出错：" + rootMessage(e)));
                } catch (Exception sendEx) {
                    log.warn("[ypbin-ai] 发送错误提示失败：conversationId={}", finalConvId, sendEx);
                }
                disposeQuietly(subscriptionRef);
                emitter.complete();
            })
            .doOnComplete(() -> {
                emitter.complete();
                saveAssistantMessageAsync(finalConvId, tenantId,
                    contentBuffer.get().toString(), tokenCount.get());
            })
            .subscribe();
        subscriptionRef.set(subscription);

        emitter.onTimeout(() -> disposeQuietly(subscriptionRef));
        emitter.onError(e -> disposeQuietly(subscriptionRef));

        return emitter;
    }

    /**
     * 构造仅推送一条错误帧（event:error）的 SSE 响应。
     */
    private static SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (Exception e) {
            log.warn("[ypbin-ai] 发送错误提示失败", e);
        }
        emitter.complete();
        return emitter;
    }

    private static void disposeQuietly(AtomicReference<Disposable> ref) {
        Disposable d = ref.get();
        if (d != null) {
            d.dispose();
        }
    }

    /**
     * 提取异常链最深层的原因消息，避免把完整堆栈推送给前端。
     */
    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : msg;
    }

    @Override
    public List<AiConversationResp> listConversations() {
        Long userId = LoginHelper.getUserId();
        List<AiConversation> list = conversationMapper.selectList(
            new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getUpdateTime));
        return list.stream().map(this::toConversationResp).toList();
    }

    @Override
    public PageResult<AiMessageResp> pageMessages(Long conversationId, PageQuery query) {
        Long userId = LoginHelper.getUserId();
        requireOwnedConversation(conversationId, userId);
        Page<AiMessage> page = messageMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()),
            new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getCreateTime));
        List<AiMessageResp> items = page.getRecords().stream().map(this::toMessageResp).toList();
        return PageResult.of(items, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiConversationResp createConversation(Long modelId) {
        Long userId = LoginHelper.getUserId();
        Long tenantId = currentTenantId();
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTenantId(tenantId);
        conv.setModelId(modelId);
        conv.setTitle("新对话");
        conversationMapper.insert(conv);
        return toConversationResp(conv);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        Long userId = LoginHelper.getUserId();
        requireOwnedConversation(conversationId, userId);
        conversationMapper.deleteById(conversationId);
        // 清除 AI Memory（有 AiChatService 时才清）
        AiChatService svc = aiChatServiceProvider.getIfAvailable();
        if (svc != null) {
            svc.clearMemory(String.valueOf(conversationId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameConversation(Long conversationId, String title) {
        Long userId = LoginHelper.getUserId();
        requireOwnedConversation(conversationId, userId);
        AiConversation conv = new AiConversation();
        conv.setId(conversationId);
        conv.setTitle(title);
        conversationMapper.updateById(conv);
    }

    @Override
    @Async
    public void saveAssistantMessageAsync(Long conversationId, Long tenantId,
            String content, int tokens) {
        // 异步线程无请求上下文，按传入租户包裹执行，保证行级隔离与审计正确
        TenantContext.runWithTenant(tenantId, () -> {
            saveMessage(conversationId, tenantId, "assistant", content, tokens);
            // 写入用量日志，供统计页使用；会话查询不到时跳过，避免空 userId 插入失败
            AiConversation conv = conversationMapper.selectById(conversationId);
            if (conv == null) {
                log.warn("[ypbin-ai] 会话不存在，跳过用量记录：conversationId={}", conversationId);
                return;
            }
            AiUsageLog usage = new AiUsageLog();
            usage.setTenantId(tenantId);
            usage.setUserId(conv.getUserId());
            usage.setConversationId(conversationId);
            usage.setModelId(conv.getModelId());
            // 冗余模型名（防改名影响统计），无配置时保持 null
            if (conv.getModelId() != null) {
                AiModelConfig modelConfig = modelConfigMapper.selectById(conv.getModelId());
                if (modelConfig != null) {
                    usage.setModelName(modelConfig.getModelName());
                }
            }
            usage.setOutputTokens(tokens);
            usage.setInputTokens(0);
            usage.setTotalTokens(tokens);
            usage.setLatencyMs(0L);
            usageLogMapper.insert(usage);
        });
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }

    /**
     * 会话存在性 + 归属校验：防止同租户下越权读取/操作他人会话。
     */
    private AiConversation requireOwnedConversation(Long conversationId, Long userId) {
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return conv;
    }

    private Long ensureConversation(Long conversationId, Long userId, Long tenantId) {
        if (conversationId != null) {
            requireOwnedConversation(conversationId, userId);
            return conversationId;
        }
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTenantId(tenantId);
        conv.setTitle("新对话");
        conversationMapper.insert(conv);
        return conv.getId();
    }

    private void saveMessage(Long conversationId, Long tenantId, String role,
            String content, int tokens) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setTenantId(tenantId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokens(tokens);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    private String resolveSystemPrompt(Long templateId) {
        if (templateId == null) {
            return null;
        }
        var tpl = promptTemplateMapper.selectById(templateId);
        return tpl != null ? tpl.getTemplate() : null;
    }

    private AiConversationResp toConversationResp(AiConversation conv) {
        AiConversationResp resp = new AiConversationResp();
        resp.setId(conv.getId());
        resp.setModelId(conv.getModelId());
        resp.setTitle(conv.getTitle());
        resp.setCreateTime(conv.getCreateTime());
        resp.setUpdateTime(conv.getUpdateTime());
        return resp;
    }

    private AiMessageResp toMessageResp(AiMessage msg) {
        AiMessageResp resp = new AiMessageResp();
        resp.setId(msg.getId());
        resp.setConversationId(msg.getConversationId());
        resp.setRole(msg.getRole());
        resp.setContent(msg.getContent());
        resp.setTokens(msg.getTokens());
        resp.setCreateTime(msg.getCreateTime());
        return resp;
    }
}