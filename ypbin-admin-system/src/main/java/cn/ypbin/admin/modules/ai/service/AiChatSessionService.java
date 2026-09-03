/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service;

import cn.ypbin.admin.ai.model.req.AiChatSendReq;
import cn.ypbin.admin.ai.model.req.AiChatSessionCreateReq;
import cn.ypbin.admin.ai.model.resp.AiChatMessageResp;
import cn.ypbin.admin.ai.model.resp.AiChatSessionResp;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 会话服务接口（会话与消息管理）。
 *
 * <p>负责 {@code ai_chat_session}/{@code ai_chat_message} 的 CRUD 与对话编排；
 * 底层 AI 调用委托 starter 的 {@code cn.ypbin.starter.ai.chat.AiChatService}。</p>
 *
 * @author wenbin
 * @since 2026-08-16
 */
public interface AiChatSessionService {

    /**
     * 获取当前用户的会话列表（按最后消息时间倒序）。
     */
    List<AiChatSessionResp> listSessions();

    /**
     * 创建新会话。
     */
    Long createSession(AiChatSessionCreateReq req);

    /**
     * 删除会话（逻辑删除，同时删除关联消息）。
     */
    void deleteSession(Long sessionId);

    /**
     * 获取会话的历史消息（分页或全量，按创建时间升序）。
     */
    List<AiChatMessageResp> listMessages(Long sessionId);

    /**
     * 发送消息（同步，返回完整 AI 响应）。
     */
    AiChatMessageResp sendMessage(AiChatSendReq req);

    /**
     * 发送消息（流式，返回 SSE Emitter）。
     */
    SseEmitter sendMessageStream(AiChatSendReq req);

    /**
     * 重新生成最后一条 AI 响应。
     */
    AiChatMessageResp regenerateLastMessage(Long sessionId);

    /**
     * 更新会话标题。
     */
    void updateSessionTitle(Long sessionId, String title);

    /**
     * 置顶/取消置顶会话。
     */
    void toggleSessionPin(Long sessionId);
}
