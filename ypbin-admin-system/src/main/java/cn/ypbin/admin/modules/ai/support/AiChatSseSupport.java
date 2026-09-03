/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.support;

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * AI 对话 SSE 流式辅助工具。
 *
 * <p>承载 SSE 错误帧构造、订阅释放与根异常提取三个纯函数，
 * 从 {@code AiChatServiceImpl} 拆分，供流式对话主流程复用。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
public final class AiChatSseSupport {

    private static final Logger log = LoggerFactory.getLogger(AiChatSseSupport.class);

    private AiChatSseSupport() {
    }

    /** 构造只携带错误信息的 SSE 响应（立即发送并完成）。 */
    public static SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (Exception e) {
            log.warn("[ypbin-ai] 发送错误提示失败", e);
        }
        emitter.complete();
        return emitter;
    }

    /** 释放流式订阅（幂等）。 */
    public static void disposeQuietly(AtomicReference<Disposable> ref) {
        Disposable d = ref.get();
        if (d != null) {
            d.dispose();
        }
    }

    /** 提取异常链最深层原因的消息文本。 */
    public static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : msg;
    }
}
