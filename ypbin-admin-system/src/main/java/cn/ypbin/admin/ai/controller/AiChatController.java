/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.ai.model.req.AiChatSendReq;
import cn.ypbin.admin.ai.model.req.AiChatSessionCreateReq;
import cn.ypbin.admin.ai.model.resp.AiChatMessageResp;
import cn.ypbin.admin.ai.model.resp.AiChatSessionResp;
import cn.ypbin.admin.ai.service.AiChatSessionService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import cn.ypbin.starter.log.annotation.Log;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
public class AiChatController {

    private final AiChatSessionService chatSessionService;

    /**
     * 获取当前用户的所有会话列表。
     */
    @GetMapping("/sessions")
    @SaCheckPermission("ai:chat:list")
    public R<List<AiChatSessionResp>> listSessions() {
        return R.ok(chatSessionService.listSessions());
    }

    /**
     * 创建新会话。
     */
    @Idempotent
    @PostMapping("/sessions")
    @SaCheckPermission("ai:chat:create")
    @Log(value = "创建对话会话", module = "AI 对话")
    public R<Long> createSession(@Valid @RequestBody AiChatSessionCreateReq req) {
        return R.ok(chatSessionService.createSession(req));
    }

    /**
     * 删除会话。
     */
    @Idempotent
    @DeleteMapping("/sessions/{id}")
    @SaCheckPermission("ai:chat:delete")
    @Log(value = "删除对话会话", module = "AI 对话")
    public R<Void> deleteSession(@PathVariable Long id) {
        chatSessionService.deleteSession(id);
        return R.ok();
    }

    /**
     * 获取会话消息历史。
     */
    @GetMapping("/sessions/{id}/messages")
    @SaCheckPermission("ai:chat:list")
    public R<List<AiChatMessageResp>> listMessages(@PathVariable Long id) {
        return R.ok(chatSessionService.listMessages(id));
    }

    /**
     * 发送消息（同步）。
     */
    @Idempotent
    @PostMapping("/send")
    @SaCheckPermission("ai:chat:send")
    @Log(value = "发送对话消息", module = "AI 对话")
    public R<AiChatMessageResp> sendMessage(@Valid @RequestBody AiChatSendReq req) {
        return R.ok(chatSessionService.sendMessage(req));
    }

    /**
     * 发送消息（流式 SSE）。
     *
     * <p>SSE 长连接由前端唯一触发、请求与响应同生命周期，防重组件不适用于流式语义；
     * 消息落库由 {@link AiChatSessionService#sendMessageStream} 内部保障幂等。</p>
     */
    @Log(value = "发送对话消息（流式）", module = "AI 对话")
    @PostMapping("/stream")
    @SaCheckPermission("ai:chat:send")
    public SseEmitter sendMessageStream(@Valid @RequestBody AiChatSendReq req) {
        return chatSessionService.sendMessageStream(req);
    }

    /**
     * 重新生成最后一条响应。
     */
    @Idempotent
    @PostMapping("/sessions/{id}/regenerate")
    @SaCheckPermission("ai:chat:send")
    @Log(value = "重新生成响应", module = "AI 对话")
    public R<AiChatMessageResp> regenerate(@PathVariable Long id) {
        return R.ok(chatSessionService.regenerateLastMessage(id));
    }

    /**
     * 更新会话标题。
     */
    @Idempotent
    @PutMapping("/sessions/{id}/title")
    @SaCheckPermission("ai:chat:edit")
    @Log(value = "修改会话标题", module = "AI 对话")
    public R<Void> updateTitle(@PathVariable Long id, @RequestParam String title) {
        chatSessionService.updateSessionTitle(id, title);
        return R.ok();
    }

    /**
     * 置顶/取消置顶会话。
     */
    @Idempotent
    @PutMapping("/sessions/{id}/pin")
    @SaCheckPermission("ai:chat:edit")
    @Log(value = "切换会话置顶状态", module = "AI 对话")
    public R<Void> togglePin(@PathVariable Long id) {
        chatSessionService.toggleSessionPin(id);
        return R.ok();
    }
}
