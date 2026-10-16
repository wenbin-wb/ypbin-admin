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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link WsTicketService} 单元测试。
 *
 * <p>核心不变量是「一次性」与「房间绑定」：ticket 一旦被核销就不能再用（防重放建立多条连接），
 * 且用 A 房间的票不能订阅 B 房间。这两条一旦失效，等于实时通道失去鉴权意义。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class WsTicketServiceTest {

    private final WsTicketService service = new WsTicketService();

    @Test
    void issueShouldReturnNonBlankTicket() {
        try (MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class)) {
            String ticket = service.issue(1L, 100L);
            assertThat(ticket).isNotBlank();
            // 签发时必须把「用户:房间」绑定写入缓存（否则后续无法校验归属）
            cache.verify(() -> CacheUtils.set(anyString(), eq("1:100"), any()));
        }
    }

    @Test
    void consumeShouldReturnUserIdWhenTicketMatchesRoom() {
        try (MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class)) {
            cache.when(() -> CacheUtils.get(anyString(), eq(String.class))).thenReturn("7:100");
            cache.when(() -> CacheUtils.compareAndDelete(anyString(), any())).thenReturn(true);

            assertThat(service.consume("ticket-abc", 100L)).isEqualTo(7L);
        }
    }

    @Test
    void consumeShouldRejectUnknownOrExpiredTicket() {
        try (MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class)) {
            cache.when(() -> CacheUtils.get(anyString(), eq(String.class))).thenReturn(null);

            assertThatThrownBy(() -> service.consume("ticket-abc", 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效或已过期");
        }
    }

    @Test
    void consumeShouldRejectReplayWhenCompareAndDeleteFails() {
        try (MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class)) {
            // 缓存里还在，但删除失败 = 已被另一并发连接抢先核销 → 必须拒绝（防同一票建多条连接）
            cache.when(() -> CacheUtils.get(anyString(), eq(String.class))).thenReturn("7:100");
            cache.when(() -> CacheUtils.compareAndDelete(anyString(), any())).thenReturn(false);

            assertThatThrownBy(() -> service.consume("ticket-abc", 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被使用");
        }
    }

    @Test
    void consumeShouldRejectTicketBoundToAnotherRoom() {
        try (MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class)) {
            // 票绑的是 100 房间，却拿来连 200 房间 → 拒绝
            cache.when(() -> CacheUtils.get(anyString(), eq(String.class))).thenReturn("7:100");
            cache.when(() -> CacheUtils.compareAndDelete(anyString(), any())).thenReturn(true);

            assertThatThrownBy(() -> service.consume("ticket-abc", 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不匹配");
        }
    }

    @Test
    void consumeShouldRejectBlankTicket() {
        assertThatThrownBy(() -> service.consume("  ", 100L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("缺少连接凭证");
    }
}
