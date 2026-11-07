/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.support;

import java.util.concurrent.atomic.AtomicReference;
import reactor.core.Disposable;

/**
 * AI 对话 SSE 流式辅助工具（本仓仅保留 starter 未提供的那一个能力）。
 *
 * <p><strong>为什么只剩 {@code disposeQuietly}</strong>：SSE 错误帧构造与根因提取属通用传输能力，
 * starter 的 {@code SseStreamBridge}（{@code ypbin-starter-messaging}）已提供同语义的
 * {@code errorEmitter}/{@code rootMessage}，本仓原有的两份副本是与 starter 逐行重复的实现，
 * 且会随 starter 演进漂移（starter 版带参数校验与可定制选项，副本没有）。故二者已删除，
 * 调用方改为直接使用 {@link cn.ypbin.starter.messaging.sse.SseStreamBridge}。</p>
 *
 * <p>而 {@code disposeQuietly} 保留在业务侧：starter 的流式桥接用 {@code Disposable.Swap} 管理订阅，
 * 本仓手工 {@code subscribe()} 的路径持有的是 {@code AtomicReference<Disposable>}，类型不同；
 * 为统一它需要在 starter 补一个重载（新增 API，属独立评估项），不在本次改动范围。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
public final class AiChatSseSupport {

    private AiChatSseSupport() {
    }

    /** 释放流式订阅（幂等）。 */
    public static void disposeQuietly(AtomicReference<Disposable> ref) {
        Disposable d = ref.get();
        if (d != null) {
            d.dispose();
        }
    }
}
