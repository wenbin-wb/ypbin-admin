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

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.annotation.PlatformAccess;
import cn.ypbin.admin.system.model.resp.OnlineUserResp;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.security.online.OnlineUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线用户管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/online-user")
@RequiredArgsConstructor
@PlatformAccess
public class OnlineUserController {

    private final OnlineUserService onlineUserService;
    private final SysUserService userService;

    @GetMapping("/list")
    @SaCheckPermission("system:online-user:list")
    public R<List<OnlineUserResp>> list(@RequestParam(required = false) String keyword) {
        return R.ok(userService.listOnlineUsers(keyword));
    }

    @Idempotent
    @DeleteMapping("/{token}")
    @SaCheckPermission("system:online-user:kickout")
    @Log(value = "强制下线用户", module = "会话管理")
    public R<Void> kickout(@PathVariable String token) {
        onlineUserService.kickoutByToken(token);
        return R.ok();
    }
}
