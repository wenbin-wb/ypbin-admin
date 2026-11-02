/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.gateway.auth;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.gateway.auth.GatewayAuthProvider;
import cn.ypbin.starter.gateway.auth.GatewayAuthResult;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityHeaders;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一鉴权提供者：校验 sa-token，签发内部可信身份头。
 *
 * <p>实现 {@link GatewayAuthProvider} SPI：读取 {@code Authorization} 头中的 token，
 * 用 sa-token-core 无状态校验（不依赖 Web 容器），校验通过后从会话取 {@link LoginUser}
 * 并签发 CONTRACT.md §6 约定的内部身份头。下游服务只信这些头，不再各自验 token。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Component
public class SaTokenGatewayAuthProvider implements GatewayAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(SaTokenGatewayAuthProvider.class);

    /**
     * 强制改密标记的会话键（与 {@code cn.ypbin.admin.common.support.PwdChangeKeys#REQUIRED} 一致）。
     *
     * <p>网关不依赖 {@code ypbin-common}（避免把业务工具带进网关），故此处保留字面量并注明来源；
     * 两处不一致时表现为「标记不生效」或「改密后仍被拦截」，由 {@code PwdChangeKeyContractTest} 钉住。</p>
     */
    static final String PWD_CHANGE_REQUIRED_KEY = "pwdChangeRequired";

    /** 强制改密期间仍放行的路径（改密本身、登出、身份查询）。 */
    private static final Set<String> PWD_CHANGE_ALLOWED_PATHS = Set.of(
        "/auth/logout",
        "/auth/user/info",
        "/auth/check",
        "/system/user/profile/password");

    @Override
    public Mono<GatewayAuthResult> authenticate(ServerWebExchange exchange) {
        String token = resolveToken(exchange);
        if (!StringUtils.hasText(token)) {
            return Mono.just(GatewayAuthResult.failure("未提供登录凭证"));
        }
        try {
            // 无状态校验：token 不存在/过期会抛 NotLoginException
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                return Mono.just(GatewayAuthResult.failure("登录状态已过期，请重新登录"));
            }
            LoginUser loginUser = readLoginUser(loginId.toString());
            if (loginUser == null) {
                return Mono.just(GatewayAuthResult.failure("登录用户不存在"));
            }
            if (readMustChangePassword(loginId) && !isPwdChangeAllowed(exchange)) {
                return Mono.just(GatewayAuthResult.failure("当前为初始密码，请先修改密码后再继续操作"));
            }
            return Mono.just(GatewayAuthResult.success(buildTrustedHeaders(loginUser)));
        } catch (NotLoginException e) {
            // 仅未登录/过期走 401 语义
            return Mono.just(GatewayAuthResult.failure("登录状态已过期，请重新登录"));
        } catch (RuntimeException e) {
            // Redis/Sa-Token 存储异常与「未登录」语义不同：必须留日志，否则会被误判为凭证过期
            log.error("网关鉴权异常，拒绝本次请求", e);
            return Mono.just(GatewayAuthResult.failure("认证服务暂不可用，请稍后重试"));
        }
    }

    /**
     * 从请求头解析 token：优先 Bearer 前缀，其次裸 token。
     */
    private String resolveToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        return authorization.startsWith("Bearer ")
            ? authorization.substring(7).trim()
            : authorization.trim();
    }

    /**
     * 从 sa-token 会话读取登录用户（登录时由 auth-svc 写入 {@code UserContext.KEY_LOGIN_USER}）。
     */
    private LoginUser readLoginUser(String loginId) {
        try {
            Object value = StpUtil.getSessionByLoginId(loginId).get(
                UserContext.KEY_LOGIN_USER);
            return value instanceof LoginUser loginUser ? loginUser : null;
        } catch (Exception e) {
            // 会话读取失败视为用户信息缺失，由调用方按未登录处理
            return null;
        }
    }

    /**
     * 读取「是否必须先改密」标记（登录时写入，改密后随会话被踢出失效）。
     *
     * @param loginId 登录 ID
     * @return true=必须改密；读取失败时放行并记录日志（避免存储抖动把所有用户锁在改密页）
     */
    private boolean readMustChangePassword(Object loginId) {
        try {
            Object value = StpUtil.getSessionByLoginId(loginId.toString())
                .get(PWD_CHANGE_REQUIRED_KEY);
            return Boolean.TRUE.equals(value);
        } catch (RuntimeException e) {
            log.error("读取强制改密标记失败：loginId={}", loginId, e);
            return false;
        }
    }

    /**
     * 强制改密期间是否允许该路径（改密、登出、身份查询）。
     *
     * @param exchange 当前请求
     * @return true=放行
     */
    private boolean isPwdChangeAllowed(ServerWebExchange exchange) {
        return PWD_CHANGE_ALLOWED_PATHS.contains(exchange.getRequest().getPath().value());
    }

    /**
     * 构建下游可信身份头（与 starter {@link IdentityHeaders} 常量对齐）。
     */
    private Map<String, String> buildTrustedHeaders(LoginUser loginUser) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(IdentityHeaders.USER_ID, String.valueOf(loginUser.getId()));
        if (StringUtils.hasText(loginUser.getUsername())) {
            headers.put(IdentityHeaders.USER_NAME, loginUser.getUsername());
        }
        if (loginUser.getTenantId() != null) {
            headers.put(IdentityHeaders.TENANT_ID, String.valueOf(loginUser.getTenantId()));
        }
        if (loginUser.getDeptId() != null) {
            headers.put(IdentityHeaders.DEPT_ID, String.valueOf(loginUser.getDeptId()));
        }
        if (loginUser.getRoles() != null && !loginUser.getRoles().isEmpty()) {
            headers.put(IdentityHeaders.ROLES, String.join(",", loginUser.getRoles()));
        }
        return headers;
    }
}
