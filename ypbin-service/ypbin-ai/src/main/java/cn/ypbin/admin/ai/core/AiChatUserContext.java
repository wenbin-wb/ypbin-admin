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

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 管理端 AI 对话的「已登录用户」上下文。
 *
 * <p><b>为什么需要单独一份</b>：{@code @Tool} 由 starter-ai 全局注册，管理端对话、
 * 知识库公开分享、网页挂件三条链路上的 {@code ChatClient} 都会挂载同一批工具。
 * 公开入口是匿名访问（{@code AiPublicAccessSupport} 只绑定了知识库所属租户），
 * 若工具只依据「租户」判断调用者身份，匿名访客就能借 AI 把该租户的用户列表
 * 问出来。因此这里显式标记「本次对话来自管理端登录会话」：只有管理端对话链路
 * 会写入该上下文，工具发现为空时必须拒绝执行。</p>
 *
 * <p>用户身份本身来自 {@code IdentityContext}（普通 ThreadLocal，Reactor 工作线程
 * 读不到），跨线程传播由 {@link AiChatUserThreadLocalAccessor} 从 Reactor Context
 * 还原，与 {@code TenantThreadLocalAccessor} 同一机制。禁止在工具里直接调用
 * {@code StpUtil}：Sa-Token 上下文依赖 HTTP 请求线程。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
public final class AiChatUserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private AiChatUserContext() {
    }

    /**
     * 写入 Reactor Context 的用户快照。
     *
     * @param userId 当前登录用户 ID
     */
    public record Snapshot(Long userId) {
    }

    /**
     * 当前线程上的登录用户 ID（管理端 AI 对话内有效）。
     *
     * @return 用户 ID；公开访问链路为空
     */
    public static Optional<Long> getUserId() {
        return Optional.ofNullable(USER_ID.get());
    }

    /**
     * 生成当前线程的用户快照。
     *
     * @return 快照；无用户时返回 {@code null}（不写入 Reactor Context）
     */
    public static Snapshot snapshot() {
        Long userId = USER_ID.get();
        return userId == null ? null : new Snapshot(userId);
    }

    /**
     * 还原快照到当前线程。
     *
     * @param userId 用户 ID（可空，空表示清除）
     */
    public static void set(Long userId) {
        if (userId == null) {
            USER_ID.remove();
        } else {
            USER_ID.set(userId);
        }
    }

    /**
     * 清除当前线程的用户上下文。
     */
    public static void clear() {
        USER_ID.remove();
    }

    /**
     * 在指定用户上下文中执行（阻塞式调用用）。
     *
     * @param userId   用户 ID
     * @param supplier 执行体
     * @param <T>      返回值类型
     * @return 执行结果
     */
    public static <T> T executeWithUser(Long userId, Supplier<T> supplier) {
        Long previous = USER_ID.get();
        set(userId);
        try {
            return supplier.get();
        } finally {
            set(previous);
        }
    }
}
