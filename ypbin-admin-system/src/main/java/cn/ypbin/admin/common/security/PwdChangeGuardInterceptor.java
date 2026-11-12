/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.admin.common.constant.PwdChangeKeys;
import cn.ypbin.starter.core.exception.BusinessException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 强制改密守卫：登录标记为「必须先改密」时，只放行改密/登出/身份查询。
 *
 * <p>单体线没有网关，由 MVC 拦截器承担与微服务网关同一职责；标记在登录时写入 sa-token 会话，
 * 改密成功后会话被踢出、重新登录即解除。</p>
 *
 * @author wenbin
 * @since 2026-11-02
 */
@Component
public class PwdChangeGuardInterceptor implements HandlerInterceptor {

    /** 强制改密期间仍放行的路径（改密本身、登出、身份查询）。 */
    private static final Set<String> ALLOWED_PATHS = Set.of(
        "/auth/logout",
        "/auth/user/info",
        "/auth/check",
        "/system/user/profile/password");

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            // 容器错误派发（如 404 转发到 /error）不在 Sa-Token 上下文内，
            // 继续调用 StpUtil 会抛 SaTokenContextException 覆盖原始错误
            return true;
        }
        if (!StpUtil.isLogin()) {
            // 未登录请求交给原有鉴权链路处理，避免把 401 改写成业务错误
            return true;
        }
        Object required = StpUtil.getSession().get(PwdChangeKeys.REQUIRED);
        if (!Boolean.TRUE.equals(required)) {
            return true;
        }
        if (ALLOWED_PATHS.contains(request.getRequestURI())) {
            return true;
        }
        throw new BusinessException("当前为初始密码，请先修改密码后再继续操作");
    }
}
