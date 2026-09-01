/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common;

import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.admin.common.IdentityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部身份头过滤器（微服务下游专用）。
 *
 * <p>网关校验 token 后签发 {@code X-User-Id/X-User-Name/X-Tenant-Id/X-Dept-Id/X-Roles}
 * 可信身份头（CONTRACT.md §6）。各业务服务装配本过滤器，从这些头构建 {@link LoginUser}
 * 写入 {@link IdentityContext}，供业务代码无感知读取当前用户——服务自身不再校验 token。</p>
 *
 * <p>安全前提：服务只暴露在网关内网，外部请求无法直达（若需直连请自行加白名单或改为
 * 服务间签名校验）。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public class IdentityHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_DEPT_ID = "X-Dept-Id";
    public static final String HEADER_ROLES = "X-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        if (StringUtils.hasText(userId)) {
            LoginUser loginUser = new LoginUser();
            loginUser.setId(Long.valueOf(userId));
            String username = request.getHeader(HEADER_USER_NAME);
            if (StringUtils.hasText(username)) {
                loginUser.setUsername(username);
            }
            String tenantId = request.getHeader(HEADER_TENANT_ID);
            if (StringUtils.hasText(tenantId)) {
                loginUser.setTenantId(Long.valueOf(tenantId));
            }
            String deptId = request.getHeader(HEADER_DEPT_ID);
            if (StringUtils.hasText(deptId)) {
                loginUser.setDeptId(Long.valueOf(deptId));
            }
            String roles = request.getHeader(HEADER_ROLES);
            if (StringUtils.hasText(roles)) {
                Set<String> roleSet = Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
                loginUser.setRoles(roleSet);
            }
            IdentityContext.setLoginUser(loginUser);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            IdentityContext.clear();
        }
    }
}
