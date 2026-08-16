/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service;

import cn.ypbin.admin.system.ai.entity.AiConversation;
import cn.ypbin.admin.system.ai.model.resp.AiConversationResp;
import cn.ypbin.admin.system.ai.model.resp.AiMessageResp;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话业务接口。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiChatBizService {

    /**
     * 流式对话（SSE 推送增量 token）。
     *
     * <p>会话不存在时自动创建，首条消息截取前 50 字作为标题。
     *
     * @param conversationId  会话 ID，null 则新建
     * @param message         用户消息
     * @param knowledgeBaseId 关联知识库 ID（启用 RAG），null 则普通对话
     * @param promptTemplateId 使用的 Prompt 模板 ID，null 则用默认系统提示词
     * @return SSE Emitter，Controller 直接返回给前端
     */
    SseEmitter chat(Long conversationId, String message, Long knowledgeBaseId, Long promptTemplateId);

    /**
     * 获取当前用户的会话列表。
     */
    List<AiConversationResp> listConversations();

    /**
     * 获取指定会话的历史消息。
     */
    PageResult<AiMessageResp> pageMessages(Long conversationId, PageQuery query);

    /**
     * 新建会话（无需立即发消息，前端可先建再发）。
     */
    AiConversationResp createConversation(Long modelId);

    /**
     * 删除会话（同时清除 AI Memory）。
     */
    void deleteConversation(Long conversationId);

    /**
     * 修改会话标题。
     */
    void renameConversation(Long conversationId, String title);

    /**
     * 异步保存助手回复消息（流式结束后落库，不阻塞 SSE 流）。
     *
     * <p>异步线程无请求上下文，租户 ID 由调用方（请求线程）显式传入。
     */
    void saveAssistantMessageAsync(Long conversationId, Long tenantId,
            String content, int tokens);
}
