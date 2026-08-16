/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.ai.model.req.AiChatReq;
import cn.ypbin.admin.system.ai.model.resp.AiConversationResp;
import cn.ypbin.admin.system.ai.model.resp.AiMessageResp;
import cn.ypbin.admin.system.ai.service.AiChatBizService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话接口。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController extends BaseController {

    private final AiChatBizService chatBizService;

    /** 流式对话（SSE text/event-stream），每个 token 作为一帧推送 */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("ai:chat:send")
    public SseEmitter chat(@Valid @RequestBody AiChatReq req) {
        return chatBizService.chat(
            req.getConversationId(),
            req.getMessage(),
            req.getKnowledgeBaseId(),
            req.getPromptTemplateId());
    }

    /** 获取当前用户的会话列表 */
    @GetMapping("/conversations")
    @SaCheckPermission("ai:chat:send")
    public R<List<AiConversationResp>> listConversations() {
        return ok(chatBizService.listConversations());
    }

    /** 新建会话 */
    @PostMapping("/conversations")
    @SaCheckPermission("ai:chat:send")
    public R<AiConversationResp> createConversation(
            @RequestParam(required = false) Long modelId) {
        return ok(chatBizService.createConversation(modelId));
    }

    /** 获取会话历史消息 */
    @GetMapping("/conversations/{conversationId}/messages")
    @SaCheckPermission("ai:chat:send")
    public R<PageResult<AiMessageResp>> pageMessages(
            @PathVariable Long conversationId, PageQuery query) {
        return ok(chatBizService.pageMessages(conversationId, query));
    }

    /** 删除会话（同时清除 AI Memory） */
    @DeleteMapping("/conversations/{conversationId}")
    @SaCheckPermission("ai:chat:send")
    public R<Void> deleteConversation(@PathVariable Long conversationId) {
        chatBizService.deleteConversation(conversationId);
        return ok();
    }

    /** 修改会话标题 */
    @PutMapping("/conversations/{conversationId}/title")
    @SaCheckPermission("ai:chat:send")
    public R<Void> renameConversation(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> body) {
        chatBizService.renameConversation(conversationId, body.get("title"));
        return ok();
    }
}
