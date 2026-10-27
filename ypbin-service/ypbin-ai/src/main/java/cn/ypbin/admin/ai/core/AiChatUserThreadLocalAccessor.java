/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.core;

import io.micrometer.context.ThreadLocalAccessor;
import org.springframework.stereotype.Component;

/**
 * {@link AiChatUserContext} 的 Reactor 跨线程传播器。
 *
 * <p>{@code @Tool} 在 Reactor 工作线程上执行，而登录用户来自 HTTP 请求线程的
 * {@code IdentityContext}（普通 ThreadLocal）。对话流在 {@code contextWrite} 里写入
 * 用户快照，本类在每个 Scheduler 切换时把它还原到当前线程，工具即可判断
 * 「本次对话是否来自管理端登录会话」。</p>
 *
 * <p>与 {@code TenantThreadLocalAccessor} 同属 micrometer context 的
 * {@link ThreadLocalAccessor} 机制，由 Spring Boot 自动注册，无需手动挂载。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
@Component
public class AiChatUserThreadLocalAccessor
        implements ThreadLocalAccessor<AiChatUserContext.Snapshot> {

    /** Reactor Context 中的键，写入方与读取方共用 */
    public static final String KEY = "cn.ypbin.admin.ai.chat.user";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public AiChatUserContext.Snapshot getValue() {
        return AiChatUserContext.snapshot();
    }

    @Override
    public void setValue(AiChatUserContext.Snapshot value) {
        AiChatUserContext.set(value == null ? null : value.userId());
    }

    @Override
    public void setValue() {
        AiChatUserContext.clear();
    }
}
