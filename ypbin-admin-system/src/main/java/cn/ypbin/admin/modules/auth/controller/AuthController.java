/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.controller;

import cn.ypbin.admin.modules.system.model.req.LoginReq;
import cn.ypbin.admin.modules.system.model.req.RegisterCodeSendReq;
import cn.ypbin.admin.modules.system.model.req.RegisterReq;
import cn.ypbin.admin.modules.system.model.resp.LoginResp;
import cn.ypbin.admin.modules.system.model.resp.RegisterOptionsResp;
import cn.ypbin.admin.modules.system.model.resp.RouteResp;
import cn.ypbin.admin.modules.system.model.resp.UserInfoResp;
import cn.ypbin.admin.modules.auth.service.AuthService;
import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.tools.limiter.RateLimit;
import cn.ypbin.starter.web.util.WebRequestUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与当前用户接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    /**
     * 账号密码登录。
     */
    @Log(value = "登录", module = "认证")
    @RateLimit(key = "#req.username", count = 10, window = 60, message = "登录过于频繁，请稍后再试")
    @PostMapping("/auth/login")
    public R<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req, WebRequestUtils.clientIp(clientIpResolver)));
    }

    /**
     * 用户自助注册（公开入口，开关见系统参数 {@code REGISTER_ENABLED}）。
     *
     * <p>与登录入口一致按 IP 限流：注册会落库并建立会话，是比登录更重的写操作，
     * 不限流时脚本可批量灌入账号。</p>
     */
    /**
     * 注册入口的公开配置（是否开启 / 验证渠道 / 是否需要行为验证码）。
     */
    @GetMapping("/auth/register/options")
    public R<RegisterOptionsResp> registerOptions() {
        return R.ok(authService.registerOptions());
    }

    /**
     * 发送注册验证码（手机/邮箱渠道由系统参数 REGISTER_VERIFY_MODE 决定）。
     */
    @Log(value = "发送注册验证码", module = "认证")
    @RateLimit(count = 5, window = 60, message = "验证码发送过于频繁，请稍后再试")
    @PostMapping("/auth/register/code/send")
    public R<Void> sendRegisterCode(@Valid @RequestBody RegisterCodeSendReq req) {
        authService.sendRegisterCode(req);
        return R.ok();
    }

    @Log(value = "用户注册", module = "认证")
    @RateLimit(count = 5, window = 60, message = "注册过于频繁，请稍后再试")
    @PostMapping("/auth/register")
    public R<LoginResp> register(@Valid @RequestBody RegisterReq req) {
        return R.ok(authService.register(req, WebRequestUtils.ip()));
    }

    /**
     * 退出登录。
     */
    @Log(value = "登出", module = "认证")
    @PostMapping("/auth/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/auth/user/info")
    public R<UserInfoResp> userInfo() {
        return R.ok(authService.currentUserInfo());
    }

    /**
     * 获取当前登录用户的权限码集合。
     */
    @GetMapping("/auth/codes")
    public R<List<String>> codes() {
        return R.ok(authService.currentPermissions());
    }

    /**
     * 获取当前登录用户的客户端路由树。
     */
    @GetMapping("/auth/menu/all")
    public R<List<RouteResp>> menuAll() {
        return R.ok(authService.currentRoutes());
    }
}
