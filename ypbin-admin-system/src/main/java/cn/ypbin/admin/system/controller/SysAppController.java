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
import cn.ypbin.admin.system.model.req.SysAppSaveReq;
import cn.ypbin.admin.system.model.resp.AppCredentialResp;
import cn.ypbin.admin.system.model.resp.AppResp;
import cn.ypbin.admin.system.service.SysAppService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.log.annotation.Log;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放应用管理接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/system/app")
@RequiredArgsConstructor
@PlatformAccess
public class SysAppController extends BaseController {

    private final SysAppService appService;

    @GetMapping("/list")
    @SaCheckPermission("system:app:list")
    public R<List<AppResp>> list() {
        return ok(appService.listApps());
    }

    @Log(value = "新增开放应用", module = "开放应用")
    @PostMapping
    @SaCheckPermission("system:app:add")
    public R<AppCredentialResp> create(@Valid @RequestBody SysAppSaveReq req) {
        return ok(appService.createApp(req));
    }

    @Log(value = "重置应用密钥", module = "开放应用")
    @PutMapping("/{id}/reset-secret")
    @SaCheckPermission("system:app:reset-secret")
    public R<AppCredentialResp> resetSecret(@PathVariable Long id) {
        return ok(appService.resetSecret(id));
    }

    @Log(value = "修改开放应用", module = "开放应用")
    @PutMapping("/{id}")
    @SaCheckPermission("system:app:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysAppSaveReq req) {
        appService.updateApp(id, req);
        return ok();
    }

    @Log(value = "删除开放应用", module = "开放应用")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:app:delete")
    public R<Void> delete(@PathVariable Long id) {
        appService.removeById(id);
        return ok();
    }
}
