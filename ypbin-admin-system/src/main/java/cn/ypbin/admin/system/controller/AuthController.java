/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.controller;

import cn.ypbin.admin.system.model.req.LoginReq;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.model.resp.UserInfoResp;
import cn.ypbin.admin.system.service.AuthService;
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

    /**
     * 账号密码登录。
     */
    @Log(value = "登录", module = "认证")
    @RateLimit(count = 10, window = 60, message = "登录过于频繁，请稍后再试")
    @PostMapping("/auth/login")
    public R<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req, WebRequestUtils.ip()));
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
    @GetMapping("/user/info")
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
    @GetMapping("/menu/all")
    public R<List<RouteResp>> menuAll() {
        return R.ok(authService.currentRoutes());
    }
}
