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

import cn.ypbin.starter.core.util.RequestIdUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求链路 ID 兜底：单体没有网关注入 {@code X-Request-Id} 时补一个。
 *
 * <p>starter 的 {@code RequestUtils.getHeader} 在头缺失时返回占位串 {@code unknown}，
 * 而 {@code RequestIdUtils.sanitizeOrGenerate} 会把它当作合法值原样保留——结果埋点、
 * 操作日志里的链路 ID 恒为 {@code unknown}，无法把一次请求的日志串起来。</p>
 *
 * <p>这里在过滤器链最前面生成/透传链路 ID：客户端或反向代理给了合法值就沿用（去控制字符、
 * 限长），否则生成新值；同时写回响应头，方便前端排障时拿到同一条链路 ID。下游所有读取
 * {@code X-Request-Id} 的组件看到的是同一份值。</p>
 *
 * @author wenbin
 * @since 2026-11-11
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** 链路 ID 请求/响应头。 */
    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String sanitized = RequestIdUtils.sanitize(request.getHeader(HEADER));
        String requestId = sanitized == null ? RequestIdUtils.generate() : sanitized;
        response.setHeader(HEADER, requestId);
        chain.doFilter(new RequestIdRequestWrapper(request, requestId), response);
    }

    /**
     * 让下游 {@code getHeader("X-Request-Id")} 读到补全后的链路 ID。
     */
    private static final class RequestIdRequestWrapper extends HttpServletRequestWrapper {

        private final String requestId;

        private RequestIdRequestWrapper(HttpServletRequest request, String requestId) {
            super(request);
            this.requestId = requestId;
        }

        @Override
        public String getHeader(String name) {
            if (HEADER.equalsIgnoreCase(name)) {
                return requestId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(requestId));
            }
            return super.getHeaders(name);
        }
    }
}
