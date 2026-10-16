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

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 房间实时通道路由注册。
 *
 * <p>暴露两条路径（按端侧既有契约）：</p>
 * <ul>
 *   <li>{@code /cardtab/ws/room/{roomId}}：牌账清房间</li>
 *   <li>{@code /familymenu/ws/room/{roomId}}：家庭菜单餐厅</li>
 * </ul>
 *
 * <p><b>鉴权</b>：两条路径都走 {@link WsTicketHandshakeInterceptor}（一次性 ticket 核销）。
 * 端侧不能自定义 WS 请求头，故不能复用 Bearer；且下游服务无法自验 token，只能在
 * 「HTTP 换 ticket → WS 带 ticket」这一步完成鉴权。</p>
 *
 * <p><b>部署前提</b>：网关需为这两条路径配置 WS 路由（{@code lb:ws://ypbin-miniapp}）
 * 并放行鉴权，nginx 需透传 {@code Upgrade}/{@code Connection} 头；微信后台需把
 * {@code wss://<域名>} 配为 socket 合法域名。三者缺一，端侧都会连接失败并降级。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MiniappWebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final WsTicketHandshakeInterceptor wsTicketHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/cardtab/ws/room/{roomId}")
            .addInterceptors(wsTicketHandshakeInterceptor)
            // 小程序与 H5 调试都属跨域场景；此处不做来源校验，凭据由一次性 ticket 保证
            .setAllowedOriginPatterns("*");
        registry.addHandler(roomWebSocketHandler, "/familymenu/ws/room/{roomId}")
            .addInterceptors(wsTicketHandshakeInterceptor)
            .setAllowedOriginPatterns("*");
    }
}
