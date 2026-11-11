/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.controller;

import cn.ypbin.admin.auth.dto.LoginReq;
import cn.ypbin.admin.auth.dto.RegisterCodeSendReq;
import cn.ypbin.admin.auth.dto.RegisterOptionsResp;
import cn.ypbin.admin.auth.dto.RegisterReq;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.model.resp.UserInfoResp;
import cn.ypbin.admin.auth.service.AuthService;
import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.tools.limiter.RateLimit;
import cn.ypbin.starter.web.util.WebRequestUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（微服务版）。
 *
 * @author wenbin
 * @since 2026-09-01
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    /**
     * 账号密码登录。
     *
     * <p>与手机验证码登录入口保持一致：只加 {@code @Log}，不加 {@code @Idempotent}——登录本身已由
     * 行为验证码与账号维度锁定（{@code PasswordAttemptLimiter}）防爆破，重复提交拦截会误伤
     * "密码输错后立即重试"的正常用户。</p>
     */
    @Log(value = "账号密码登录", module = "认证")
    @PostMapping("/login")
    public R<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req, WebRequestUtils.clientIp(clientIpResolver),
            WebRequestUtils.header(HttpHeaders.USER_AGENT)));
    }

    /**
     * 用户自助注册（公开入口，开关见系统参数 {@code REGISTER_ENABLED}）。
     *
     * <p>与登录入口一致按 IP 限流：注册会落库并自动建立会话，是比登录更重的写操作，
     * 不限流时脚本可批量灌入账号。</p>
     */
    /**
     * 注册入口的公开配置（是否开启 / 验证渠道 / 是否需要行为验证码）。
     */
    @GetMapping("/register/options")
    public R<RegisterOptionsResp> registerOptions() {
        return R.ok(authService.registerOptions());
    }

    /**
     * 发送注册验证码（手机/邮箱渠道由系统参数 REGISTER_VERIFY_MODE 决定）。
     */
    @Log(value = "发送注册验证码", module = "认证")
    // 兜底上限 60/分钟；实际阈值由 REGISTER_CODE_SEND_MAX_PER_MINUTE 决定（业务层计数）
    @RateLimit(count = 60, window = 60, message = "验证码发送过于频繁，请稍后再试")
    @PostMapping("/register/code/send")
    public R<Void> sendRegisterCode(@Valid @RequestBody RegisterCodeSendReq req) {
        authService.sendRegisterCode(req, WebRequestUtils.ip());
        return R.ok();
    }

    @Log(value = "用户注册", module = "认证")
    @RateLimit(count = 5, window = 60, message = "注册过于频繁，请稍后再试")
    @PostMapping("/register")
    public R<LoginResp> register(@Valid @RequestBody RegisterReq req) {
        return R.ok(authService.register(req, WebRequestUtils.ip(),
            WebRequestUtils.header(HttpHeaders.USER_AGENT)));
    }

    /**
     * 退出登录。
     *
     * <p>与登录入口一致：IP 与 User-Agent 在 Controller 从请求上下文取、往下传，
     * 让 Service 层不依赖 HTTP 工具（也便于单测）。</p>
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout(WebRequestUtils.clientIp(clientIpResolver),
            WebRequestUtils.header(HttpHeaders.USER_AGENT));
        return R.ok();
    }

    /**
     * 当前用户信息。
     */
    @GetMapping("/user/info")
    public R<UserInfoResp> userInfo() {
        return R.ok(authService.currentUserInfo());
    }

    /**
     * 当前用户权限码。
     */
    @GetMapping("/codes")
    public R<List<String>> codes() {
        return R.ok(authService.currentPermissions());
    }

    /**
     * 当前用户菜单路由。
     */
    @GetMapping("/menu/all")
    public R<List<RouteResp>> menuAll() {
        return R.ok(authService.currentRoutes());
    }

    /**
     * 登录态检查。
     */
    @GetMapping("/check")
    public R<Void> check() {
        return authService.checkLogin();
    }
}
