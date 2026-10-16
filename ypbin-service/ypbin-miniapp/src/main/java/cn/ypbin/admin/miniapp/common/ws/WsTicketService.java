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

import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.exception.BusinessException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * WebSocket 握手用的一次性票据（ticket）签发与核销。
 *
 * <p><b>为什么需要它</b>：小程序的 {@code uni.connectSocket} <b>不能自定义请求头</b>，无法携带
 * {@code Authorization}；而本仓微服务的下游服务<b>不具备自验 token 的能力</b>
 * （身份头模式下 {@code IdentityStpLogic} 的 token 取值恒为空，见 starter）。因此 WS 握手无法
 * 复用 HTTP 的 Bearer 鉴权，也不能在 miniapp 侧直接校验 sa-token。</p>
 *
 * <p>做法：端侧先用已登录的 HTTP 通道换取一张<b>一次性、短时效</b> ticket，再用
 * {@code ?ticket=xxx} 建立 WS 连接；服务端核销即删除（用过即废），从而把「长期令牌进 URL」
 * 的风险收敛为「短时效一次性凭证」。核销用 {@code compareAndDelete} 保证并发下只有一方能成功，
 * 杜绝同一 ticket 被重放建立多条连接。</p>
 *
 * <p><b>为什么关联房间</b>：ticket 与「用户 + 房间」绑定，握手时校验目标房间与签发时一致，
 * 避免用 A 房间的 ticket 去订阅 B 房间。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Component
@RequiredArgsConstructor
public class WsTicketService {

    /** ticket 有效期：仅用于「换取后立刻建连」，故取短时效 */
    private static final Duration TTL = Duration.ofSeconds(60);

    /** 缓存 key 前缀 */
    private static final String KEY_PREFIX = "miniapp:ws:ticket:";

    /**
     * 签发一次性 ticket。
     *
     * @param userId 用户 ID
     * @param roomId 目标房间/餐厅 ID
     * @return ticket 串
     */
    public String issue(Long userId, Long roomId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        CacheUtils.set(KEY_PREFIX + ticket, userId + ":" + roomId, TTL);
        return ticket;
    }

    /**
     * 核销 ticket 并返回绑定的用户 ID。
     *
     * @param ticket ticket 串
     * @param roomId 握手请求的目标房间 ID
     * @return 用户 ID
     */
    public Long consume(String ticket, Long roomId) {
        if (ticket == null || ticket.isBlank()) {
            throw new BusinessException("缺少连接凭证");
        }
        String key = KEY_PREFIX + ticket;
        String bound = CacheUtils.get(key, String.class);
        if (bound == null) {
            // 未命中包含「不存在 / 已过期 / 已被用过」三种情况，一律拒绝（不区分以免成为探测面）
            throw new BusinessException("连接凭证无效或已过期，请重新进入");
        }
        // 用过即废：并发下只有一方能删成功，从根上杜绝重放
        if (!CacheUtils.compareAndDelete(key, bound)) {
            throw new BusinessException("连接凭证已被使用，请重新进入");
        }
        int sep = bound.indexOf(':');
        if (sep <= 0) {
            throw new BusinessException("连接凭证格式非法");
        }
        Long userId = Long.valueOf(bound.substring(0, sep));
        Long boundRoomId = Long.valueOf(bound.substring(sep + 1));
        if (!boundRoomId.equals(roomId)) {
            // 房间不匹配：拒绝，避免用 A 房间的凭证订阅 B 房间
            throw new BusinessException("连接凭证与目标房间不匹配");
        }
        return userId;
    }
}
