/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service.impl;

import cn.ypbin.admin.system.ai.entity.AiConversation;
import cn.ypbin.admin.system.ai.entity.AiMessage;
import cn.ypbin.admin.system.ai.entity.AiUsageLog;
import cn.ypbin.admin.system.ai.mapper.AiConversationMapper;
import cn.ypbin.admin.system.ai.mapper.AiMessageMapper;
import cn.ypbin.admin.system.ai.mapper.AiPromptTemplateMapper;
import cn.ypbin.admin.system.ai.mapper.AiUsageLogMapper;
import cn.ypbin.admin.system.ai.model.resp.AiConversationResp;
import cn.ypbin.admin.system.ai.model.resp.AiMessageResp;
import cn.ypbin.admin.system.ai.service.AiChatBizService;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * AI 对话业务实现。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiChatBizServiceImpl implements AiChatBizService {

    private final AiChatService aiChatService;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiPromptTemplateMapper promptTemplateMapper;
    private final AiUsageLogMapper usageLogMapper;

    @Override
    public SseEmitter chat(Long conversationId, String message, Long knowledgeBaseId,
            Long promptTemplateId) {
        Long userId = LoginHelper.getUserId();
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);

        // 会话不存在则新建
        Long finalConvId = ensureConversation(conversationId, userId, tenantId);

        // 落库用户消息
        saveMessage(finalConvId, tenantId, "user", message, 0);

        // 自动将首条消息截取为标题
        if (conversationId == null) {
            String title = message.length() > 50 ? message.substring(0, 50) + "…" : message;
            AiConversation conv = new AiConversation();
            conv.setId(finalConvId);
            conv.setTitle(title);
            conversationMapper.updateById(conv);
        }

        SseEmitter emitter = new SseEmitter(0L);

        // 收集流式 token 用于异步落库
        AtomicReference<StringBuilder> contentBuffer = new AtomicReference<>(new StringBuilder());
        AtomicInteger tokenCount = new AtomicInteger(0);

        String convIdStr = String.valueOf(finalConvId);

        // 选择对话方式
        var stream = knowledgeBaseId != null
            ? aiChatService.chatWithKnowledge(convIdStr, message, String.valueOf(knowledgeBaseId))
            : (promptTemplateId != null
                ? aiChatService.chatWithSystemPrompt(convIdStr,
                    resolveSystemPrompt(promptTemplateId), message)
                : aiChatService.chatStream(convIdStr, message));

        Disposable subscription = stream
            .doOnNext(token -> {
                try {
                    emitter.send(token);
                    contentBuffer.get().append(token);
                    tokenCount.incrementAndGet();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            })
            .doOnError(emitter::completeWithError)
            .doOnComplete(() -> {
                emitter.complete();
                // 流式结束后异步落库助手回复
                saveAssistantMessageAsync(finalConvId,
                    contentBuffer.get().toString(), tokenCount.get());
            })
            .subscribe();

        emitter.onTimeout(subscription::dispose);
        emitter.onError(e -> subscription.dispose());

        return emitter;
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
        Page<AiMessage> page = messageMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()),
            new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getCreateTime));
        List<AiMessageResp> items = page.getRecords().stream().map(this::toMessageResp).toList();
        return PageResult.of(items, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AiConversationResp createConversation(Long modelId) {
        Long userId = LoginHelper.getUserId();
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTenantId(tenantId);
        conv.setModelId(modelId);
        conv.setTitle("新对话");
        conversationMapper.insert(conv);
        return toConversationResp(conv);
    }

    @Override
    public void deleteConversation(Long conversationId) {
        conversationMapper.deleteById(conversationId);
        // 同时清除 AI Memory（conversationId 字符串与 DB ID 对应）
        aiChatService.clearMemory(String.valueOf(conversationId));
    }

    @Override
    public void renameConversation(Long conversationId, String title) {
        AiConversation conv = new AiConversation();
        conv.setId(conversationId);
        conv.setTitle(title);
        conversationMapper.updateById(conv);
    }

    @Override
    @Async
    public void saveAssistantMessageAsync(Long conversationId, String content, int tokens) {
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);
        saveMessage(conversationId, tenantId, "assistant", content, tokens);
        // 写入用量日志，供统计页使用
        AiConversation conv = conversationMapper.selectById(conversationId);
        AiUsageLog log = new AiUsageLog();
        log.setTenantId(tenantId);
        log.setUserId(conv != null ? conv.getUserId() : null);
        log.setConversationId(conversationId);
        log.setModelId(conv != null ? conv.getModelId() : null);
        log.setOutputTokens(tokens);
        log.setInputTokens(0);
        log.setTotalTokens(tokens);
        log.setLatencyMs(0L);
        usageLogMapper.insert(log);
    }

    // ---------- private helpers ----------

    private Long ensureConversation(Long conversationId, Long userId, Integer tenantId) {
        if (conversationId != null) {
            return conversationId;
        }
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setTenantId(tenantId);
        conv.setTitle("新对话");
        conversationMapper.insert(conv);
        return conv.getId();
    }

    private void saveMessage(Long conversationId, Integer tenantId, String role,
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
