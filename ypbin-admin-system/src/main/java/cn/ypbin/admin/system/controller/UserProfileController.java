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

import cn.ypbin.admin.system.model.req.ChangePasswordReq;
import cn.ypbin.admin.system.model.req.ProfileUpdateReq;
import cn.ypbin.admin.system.model.resp.ProfileResp;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.log.annotation.Log;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心接口。操作对象恒为当前登录用户，仅需登录、不挂用户管理权限。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/user/profile")
@RequiredArgsConstructor
public class UserProfileController extends BaseController {

    private final SysUserService userService;

    @GetMapping
    public R<ProfileResp> profile() {
        return ok(userService.getProfile());
    }

    @Log(value = "修改个人信息", module = "个人中心")
    @PutMapping
    public R<Void> update(@RequestBody ProfileUpdateReq req) {
        userService.updateProfile(req);
        return ok();
    }

    @Log(value = "修改密码", module = "个人中心")
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        userService.changePassword(req);
        return ok();
    }
}
