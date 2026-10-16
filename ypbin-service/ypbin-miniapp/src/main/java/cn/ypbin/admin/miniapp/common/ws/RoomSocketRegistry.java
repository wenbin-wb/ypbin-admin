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

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 房间实时通道的会话注册表与广播器。
 *
 * <p><b>部署前提</b>：广播基于进程内注册表，因此要求 ypbin-miniapp <b>单实例部署</b>
 * （见 deploy/docker-compose.yml，未配置 replicas）。若将来扩容为多实例，需要引入
 * Redis pub/sub 做跨实例转发——否则「A 实例上的连接收不到 B 实例触发的事件」这种失效
 * <b>不会有任何报错</b>，属于典型静默故障。届时务必同步改造本类。</p>
 *
 * <p>会话集合用 {@link ConcurrentHashMap#newKeySet()}，注册/注销/遍历均线程安全；
 * 发送失败只影响单个会话，故逐会话 try-catch 并在失败时移除，不让一个坏连接阻断广播。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Slf4j
@Component
public class RoomSocketRegistry {

    /** 房间 ID → 该房间的活跃会话集合 */
    private final Map<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    /**
     * 注册会话到房间。
     *
     * @param roomId  房间 ID
     * @param session WS 会话
     */
    public void register(Long roomId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /**
     * 从房间注销会话（连接关闭时调用）。
     *
     * @param roomId  房间 ID
     * @param session WS 会话
     */
    public void unregister(Long roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            // 避免 roomSessions 随历史房间无限增长（房间不再有人连接时回收 key）
            roomSessions.remove(roomId, sessions);
        }
    }

    /**
     * 向房间内所有会话广播文本消息。
     *
     * @param roomId  房间 ID
     * @param payload 已序列化的 JSON 文本
     */
    public void broadcast(Long roomId, String payload) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sendSafely(roomId, session, payload);
        }
    }

    /**
     * 单会话发送：失败即摘除该会话。
     *
     * <p>广播场景下单个连接异常不应影响其他连接，故此处容错；但必须记日志暴露
     * （静默吞掉会让「某人一直收不到推送」无从排查）。</p>
     */
    private void sendSafely(Long roomId, WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            unregister(roomId, session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException | RuntimeException e) {
            log.warn("WS 推送失败，移除该会话；roomId={}，sessionId={}", roomId, session.getId(), e);
            unregister(roomId, session);
        }
    }

    /**
     * 当前连接数（供测试与运维观察）。
     *
     * @param roomId 房间 ID
     * @return 该房间的活跃会话数
     */
    public int sessionCount(Long roomId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        return sessions == null ? 0 : sessions.size();
    }
}
