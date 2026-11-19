/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.modules.system.model.query.PlatformUserQuery;
import cn.ypbin.admin.modules.system.model.req.ResetPasswordReq;
import cn.ypbin.admin.modules.system.model.req.StatusReq;
import cn.ypbin.admin.modules.system.model.resp.PlatformUserResp;
import cn.ypbin.admin.modules.system.service.PlatformUserService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.security.platform.PlatformAccess;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台账号管理接口。
 *
 * <p>整类限定平台账号访问，非平台用户一律拒绝；仅提供查看与两个安全操作，
 * 不开放新增、删除与角色分配。</p>
 *
 * @author wenbin
 * @since 2026-11-17
 */
@PlatformAccess
@RestController
@RequestMapping("/system/platform-user")
@RequiredArgsConstructor
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    @GetMapping("/list")
    @SaCheckPermission("system:platform-user:list")
    public R<PageResult<PlatformUserResp>> list(@Valid PlatformUserQuery query) {
        return R.ok(platformUserService.pagePlatformUsers(query));
    }

    @Idempotent
    @Log(value = "修改平台账号状态", module = "平台账号")
    @PutMapping("/{id}/status")
    @SaCheckPermission("system:platform-user:edit")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusReq req) {
        platformUserService.updateStatus(id, req.getStatus());
        return R.ok();
    }

    @Idempotent
    @Log(value = "重置平台账号密码", module = "平台账号")
    @PutMapping("/{id}/reset-password")
    @SaCheckPermission("system:platform-user:reset-password")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordReq req) {
        platformUserService.resetPassword(id, req.getPassword());
        return R.ok();
    }
}
