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

import cn.ypbin.admin.system.model.req.SocialCallbackReq;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.service.SocialLoginService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.social.core.SocialService;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方登录接口：授权跳转、回调处理、绑定管理。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

    private final SocialService socialService;
    private final SocialLoginService socialLoginService;

    /**
     * 已注册的第三方平台列表。未配置任何平台时返回空集合。
     */
    @GetMapping("/platforms")
    public R<Set<String>> platforms() {
        return R.ok(socialService.sources());
    }

    /**
     * 生成授权跳转地址。
     */
    @GetMapping("/authorize/{source}")
    public R<String> authorize(@PathVariable String source) {
        return R.ok(socialService.authorizeUrl(source));
    }

    /**
     * 授权回调 → 用 code 换用户信息 → 绑定已有账号或自动注册并登录。
     */
    @Idempotent
    @Log(value = "第三方登录回调", module = "第三方登录")
    @PostMapping("/callback/{source}")
    public R<LoginResp> callback(@PathVariable String source, SocialCallbackReq req) {
        return R.ok(socialLoginService.login(source, req));
    }

    /**
     * 已登录用户绑定第三方账号。
     */
    @Idempotent
    @Log(value = "绑定第三方账号", module = "第三方登录")
    @PostMapping("/bind/{source}")
    public R<Void> bind(@PathVariable String source, SocialCallbackReq req) {
        socialLoginService.bind(source, req);
        return R.ok();
    }

    /**
     * 已登录用户解绑第三方账号。
     */
    @Idempotent
    @Log(value = "解绑第三方账号", module = "第三方登录")
    @PostMapping("/unbind/{source}")
    public R<Void> unbind(@PathVariable String source) {
        socialLoginService.unbind(source);
        return R.ok();
    }

    /**
     * 当前用户已绑定的平台列表。
     */
    @GetMapping("/bindings")
    public R<List<String>> bindings() {
        return R.ok(socialLoginService.boundPlatforms());
    }
}
