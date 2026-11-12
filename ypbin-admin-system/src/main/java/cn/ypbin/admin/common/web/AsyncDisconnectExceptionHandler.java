/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * 异步连接断开异常处理器。
 *
 * <p>SSE/长轮询客户端刷新页面、关标签或网络抖动都会让容器在写响应时抛
 * {@link AsyncRequestNotUsableException}（底层是 Broken pipe）。这是连接的正常终止方式，
 * 不是服务端故障：连接回收由 starter 的 {@code SseEmitterManager} 完成，业务无感知。</p>
 *
 * <p><strong>为什么必须单独处理</strong>：交给通用异常处理器时，它会记录 ERROR 并尝试把
 * {@code R} 按 JSON 写回；此时响应的 Content-Type 已经是 {@code text/event-stream} 且可能已提交，
 * 写 JSON 会再抛 {@code HttpMessageNotWritableException}，把一次正常断开放大成两条错误日志。
 * 本处理器不写任何响应体（{@code void}），只留一条 debug 便于按链路追溯。</p>
 *
 * @author wenbin
 * @since 2026-11-12
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AsyncDisconnectExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncDisconnectExceptionHandler.class);

    /**
     * 吞掉「客户端已断开」的异步异常，不写响应体。
     *
     * @param ex      异步响应不可用异常
     * @param request 当前请求（仅用于日志定位）
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex,
            HttpServletRequest request) {
        log.debug("客户端已断开异步连接，停止推送：uri={}, reason={}",
            request.getRequestURI(), ex.getMessage());
    }
}
