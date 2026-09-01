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
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.model.resp.UserInfoResp;
import cn.ypbin.admin.auth.service.AuthService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.web.util.WebRequestUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

    /**
     * 账号密码登录。
     */
    @PostMapping("/auth/login")
    public R<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req, WebRequestUtils.ip()));
    }

    /**
     * 退出登录。
     */
    @PostMapping("/auth/logout")
    public R<Void> logout() {
        authService.logout();
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
    @GetMapping("/auth/codes")
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
    @GetMapping("/auth/check")
    public R<Void> check() {
        return authService.checkLogin();
    }
}
