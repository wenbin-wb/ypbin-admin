/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.common.ws;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 房间实时通道处理器。
 *
 * <p>服务端<b>只推不读</b>：端侧连上后不会发送业务指令，服务端在房间数据变化时广播
 * {@code {"type":"ROOM_CHANGED"}}（牌账清）或 {@code {"type":"refresh_order"}}（家庭菜单）。
 * 收到端侧消息仅记日志——本通道不是指令通道，避免被当成可写入的入口。</p>
 *
 * <p>会话用 {@link ConcurrentWebSocketSessionDecorator} 包装后再注册：并发广播同一会话时，
 * 原生 session 不允许并发写（会抛 {@code TEXT_PARTIAL_WRITING}），装饰器负责串行化与缓冲上限。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Component
@RequiredArgsConstructor
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    /** 单会话发送缓冲上限（字节）：超出则断开，避免慢客户端拖垮服务端内存 */
    private static final int SEND_BUFFER_LIMIT = 256 * 1024;

    /** 发送超时（毫秒） */
    private static final int SEND_TIMEOUT_MS = 10_000;

    private final RoomSocketRegistry roomSocketRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long roomId = roomIdOf(session);
        if (roomId == null) {
            // 握手拦截器已保证存在；防御式关闭，避免未绑定房间的会话进入注册表
            closeQuietly(session);
            return;
        }
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
            session, SEND_TIMEOUT_MS, SEND_BUFFER_LIMIT);
        roomSocketRegistry.register(roomId, safeSession);
        log.debug("WS 已连接；roomId={}，userId={}，当前连接数={}",
            roomId, session.getAttributes().get(WsTicketHandshakeInterceptor.ATTR_USER_ID),
            roomSocketRegistry.sessionCount(roomId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 本通道只用于服务端推送，端侧上行消息不做业务处理；记录以便发现误用
        log.debug("收到 WS 上行消息（本通道仅下行）；sessionId={}，payload={}",
            session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = roomIdOf(session);
        if (roomId != null) {
            roomSocketRegistry.unregister(roomId, session);
        }
        log.debug("WS 已断开；sessionId={}，status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // 传输层错误（含缓冲超限）后连接通常不可用：记录并关闭，由端侧重连
        log.warn("WS 传输异常，关闭连接；sessionId={}", session.getId(), exception);
        closeQuietly(session);
    }

    private Long roomIdOf(WebSocketSession session) {
        Object roomId = session.getAttributes().get(WsTicketHandshakeInterceptor.ATTR_ROOM_ID);
        return roomId instanceof Long value ? value : null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            log.warn("关闭 WS 会话失败；sessionId={}", session.getId(), e);
        }
    }
}
