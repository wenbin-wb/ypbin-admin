/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common;

import cn.ypbin.starter.security.core.LoginUser;
import java.util.Optional;

/**
 * 微服务下游用户上下文（身份头驱动，不依赖 sa-token 会话）。
 *
 * <p>网关校验 token 后签发 {@code X-User-Id} 等可信身份头，{@code IdentityHeaderFilter}
 * 据此构建 {@link LoginUser} 写入本上下文；业务代码读取当前用户统一走本类。</p>
 *
 * <p>与单体版 {@code UserContext}（绑定 sa-token 会话）职责对等但实现无关，微服务版
 * 业务代码迁移时把 {@code UserContext.getUserId()} 等调用替换为 {@code IdentityContext}。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public final class IdentityContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private IdentityContext() {
    }

    /**
     * 写入当前登录用户（由 {@code IdentityHeaderFilter} 调用）。
     */
    public static void setLoginUser(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    /**
     * 当前登录用户，未登录时为空。
     */
    public static Optional<LoginUser> getLoginUser() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * 当前登录用户 ID，未登录时为空。
     */
    public static Optional<Long> getUserId() {
        LoginUser user = HOLDER.get();
        return user == null || user.getId() == null
            ? Optional.empty()
            : Optional.of(user.getId());
    }

    /**
     * 当前登录用户名，未登录时为空。
     */
    public static Optional<String> getUsername() {
        LoginUser user = HOLDER.get();
        return user == null || user.getUsername() == null
            ? Optional.empty()
            : Optional.of(user.getUsername());
    }

    /**
     * 当前租户 ID，未登录或未指定时为空。
     */
    public static Optional<Long> getTenantId() {
        LoginUser user = HOLDER.get();
        return user == null || user.getTenantId() == null
            ? Optional.empty()
            : Optional.of(user.getTenantId());
    }

    /**
     * 是否已登录（存在身份头）。
     */
    public static boolean isLogin() {
        return HOLDER.get() != null;
    }

    /**
     * 清理当前线程上下文（由 {@code IdentityHeaderFilter} 在请求结束调用）。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
