/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.config;

import cn.ypbin.admin.common.config.InternalProperties;
import cn.ypbin.admin.system.api.constant.InternalTokenConstants;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内部端点（{@code /internal/**}）调用凭证守卫。
 *
 * <p>system-svc 的登录态由网关统一校验（{@code ypbin.security.interceptor=false}），
 * {@code /internal/**} 端点原先对任意登录用户经网关转发即可访问。本守卫独立注册、仅作用于
 * {@code /internal/**}，校验请求头 {@code X-Internal-Token} 与配置凭证一致，仅放行持有内部
 * 凭证的服务间 Feign 直连（auth/ai 经拦截器自动携带）。校验失败抛业务异常，由全局异常处理器
 * 转为 HTTP 200 + {@code R.code=401}，不影响其它路径。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
public class InternalTokenGuardInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenGuardInterceptor.class);

    private final InternalProperties internalProperties;

    public InternalTokenGuardInterceptor(InternalProperties internalProperties) {
        this.internalProperties = internalProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        String configured = internalProperties.getToken();
        if (configured == null || configured.isBlank()) {
            // 凭证未配置时整体拒绝（fail-closed），严禁静默放行
            log.error("[system] 内部调用凭证未配置（ypbin.internal.token），已拒绝 /internal/** 请求：uri={}",
                request.getRequestURI());
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "内部调用凭证未配置，请先配置 ypbin.internal.token");
        }
        String presented = request.getHeader(InternalTokenConstants.TOKEN_HEADER);
        if (presented == null
            || !MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[system] 内部调用凭证校验失败，已拒绝：uri={}", request.getRequestURI());
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "内部调用凭证校验失败");
        }
        return true;
    }
}
