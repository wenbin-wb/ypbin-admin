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

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.admin.system.ai.entity.AiChatMessage;
import cn.ypbin.admin.system.ai.entity.AiChatRole;
import cn.ypbin.admin.system.ai.entity.AiChatSession;
import cn.ypbin.admin.system.ai.mapper.AiChatMessageMapper;
import cn.ypbin.admin.system.ai.mapper.AiChatRoleMapper;
import cn.ypbin.admin.system.ai.mapper.AiChatSessionMapper;
import cn.ypbin.admin.system.ai.model.req.AiChatSendReq;
import cn.ypbin.admin.system.ai.model.req.AiChatSessionCreateReq;
import cn.ypbin.admin.system.ai.model.resp.AiChatMessageResp;
import cn.ypbin.admin.system.ai.model.resp.AiChatSessionResp;
import cn.ypbin.admin.system.ai.service.AiChatBizService;
import cn.ypbin.admin.system.ai.service.AiChatService;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话服务实现。
 *
 * <p>会话/消息的 CRUD 管理层，真实 AI 调用委托给 {@link AiChatBizService}（SSE 流式）。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiChatRoleMapper roleMapper;
    private final AiChatBizService chatBizService;

    @Override
    public List<AiChatSessionResp> listSessions() {
        Long userId = LoginHelper.getUserId();
        List<AiChatSession> sessions = sessionMapper.selectList(
            new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getStatus, 1)
                .orderByDesc(AiChatSession::getIsPinned)
                .orderByDesc(AiChatSession::getLastMessageAt));
        return sessions.stream().map(this::toSessionResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSession(AiChatSessionCreateReq req) {
        Long userId = LoginHelper.getUserId();
        Long tenantId = UserContext.getTenantId().orElseThrow(
            () -> new BusinessException("无法获取当前租户上下文"));
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTenantId(tenantId);
        session.setTitle(req.getTitle() != null ? req.getTitle() : "新对话");
        session.setRoleId(req.getRoleId());
        session.setModelId(req.getModelId());
        session.setContextWindow(10);
        session.setTotalTokens(0);
        session.setMessageCount(0);
        session.setIsPinned(0);
        session.setStatus(1);
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
        // 同步接口：先创建会话（需要时），落库用户消息，委托 BizService 发起对话
        // 流式返回由 sendMessageStream 处理，同步接口暂不支持真实 AI 调用
        throw new BusinessException("请使用流式接口 /ai/chat/stream 发送消息");
    }

    @Override
    public SseEmitter sendMessageStream(AiChatSendReq req) {
        Long sessionId = req.getSessionId();
        // 新会话（无 sessionId）：角色优先走 chatWithRole，否则默认对话；
        // BizService 内部会新建 conversation 并返回，保证流式可出字。
        if (sessionId == null) {
            String rolePrompt = req.getRoleId() == null
                ? null : resolveRoleSystemPromptById(req.getRoleId());
            if (rolePrompt != null) {
                return chatBizService.chatWithRole(null, req.getContent(), rolePrompt);
            }
            return chatBizService.chat(null, req.getContent(), null, null);
        }
        // 已有会话：校验并继承上下文（session 持久化为 conversation 由后续打通）
        String rolePrompt = resolveRoleSystemPrompt(sessionId);
        if (rolePrompt != null) {
            return chatBizService.chatWithRole(sessionId, req.getContent(), rolePrompt);
        }
        return chatBizService.chat(sessionId, req.getContent(), null, null);
    }

    /**
     * 按角色 ID 直接解析系统提示词（用于新会话尚未落库时）。
     */
    private String resolveRoleSystemPromptById(Long roleId) {
        AiChatRole role = roleMapper.selectById(roleId);
        if (role == null || role.getSystemPrompt() == null || role.getSystemPrompt().isBlank()) {
            return null;
        }
        return role.getSystemPrompt();
    }

    /**
     * 解析会话绑定角色的系统提示词；无角色或非系统提示词角色返回 null。
     */
    private String resolveRoleSystemPrompt(Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getRoleId() == null) {
            return null;
        }
        AiChatRole role = roleMapper.selectById(session.getRoleId());
        if (role == null || role.getSystemPrompt() == null || role.getSystemPrompt().isBlank()) {
            return null;
        }
        return role.getSystemPrompt();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatMessageResp regenerateLastMessage(Long sessionId) {
        throw new BusinessException("重新生成功能即将上线");
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

    private AiChatSessionResp toSessionResp(AiChatSession session) {
        AiChatSessionResp resp = new AiChatSessionResp();
        BeanUtils.copyProperties(session, resp);
        if (session.getRoleId() != null) {
            AiChatRole role = roleMapper.selectById(session.getRoleId());
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
}