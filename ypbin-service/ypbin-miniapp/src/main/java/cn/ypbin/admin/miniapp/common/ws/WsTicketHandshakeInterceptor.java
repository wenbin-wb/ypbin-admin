/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.common.ws;

import cn.ypbin.starter.core.exception.BusinessException;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 房间实时通道的握手拦截器：核销一次性 ticket 并把 userId 写入会话属性。
 *
 * <p>握手阶段解析 {@code ?ticket=xxx}，经 {@link WsTicketService} 核销（一次性、60 秒时效、
 * 与房间绑定），核销通过才允许建立连接；否则拒绝握手。这样鉴权发生在建连之前，
 * 不会出现「先连上再踢」的资源消耗。</p>
 *
 * <p>房间 ID 由路径占位符 {@code {roomId}} 提供（见 {@link MiniappWebSocketConfig}），
 * 此处只负责凭据核验。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Component
@RequiredArgsConstructor
public class WsTicketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsTicketHandshakeInterceptor.class);

    /** 会话属性键：当前用户 ID */
    public static final String ATTR_USER_ID = "wsUserId";

    /** 会话属性键：房间 ID */
    public static final String ATTR_ROOM_ID = "wsRoomId";

    private final WsTicketService wsTicketService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long roomId = resolveRoomId(request.getURI());
        if (roomId == null) {
            log.warn("WS 握手缺少合法 roomId，路径={}", request.getURI().getPath());
            return false;
        }
        String ticket = resolveQueryParam(request.getURI(), "ticket");
        try {
            Long userId = wsTicketService.consume(ticket, roomId);
            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_ROOM_ID, roomId);
            return true;
        } catch (BusinessException e) {
            // 凭据问题只记 WARN（属可预期的拒绝，不是系统故障），但必须留痕
            log.warn("WS 握手鉴权失败，拒绝连接；roomId={}，原因={}", roomId, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    /** 从 /xxx/ws/room/{roomId} 中取末段作为房间 ID */
    private Long resolveRoomId(URI uri) {
        String path = uri.getPath();
        if (path == null || path.endsWith("/")) {
            return null;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        try {
            return Long.valueOf(last);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 取 query 参数（不依赖容器实现，URL query 自行解析） */
    private String resolveQueryParam(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && name.equals(pair.substring(0, eq))) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
