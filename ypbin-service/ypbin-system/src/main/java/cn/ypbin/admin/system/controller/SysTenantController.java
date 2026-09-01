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
import cn.ypbin.admin.system.model.req.TenantSaveReq;
import cn.ypbin.admin.system.model.resp.TenantResp;
import cn.ypbin.admin.system.service.SysTenantService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.security.platform.PlatformAccess;
import cn.ypbin.starter.tenant.annotation.TenantIgnore;
import cn.ypbin.starter.tools.idempotent.Idempotent;
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
 * 租户管理接口。超管全局视角，无视租户隔离。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
@TenantIgnore
@PlatformAccess
public class SysTenantController {

    private final SysTenantService tenantService;

    @GetMapping("/list")
    @SaCheckPermission("system:tenant:list")
    public R<List<TenantResp>> list() {
        return R.ok(tenantService.listTenants());
    }

    @Idempotent
    @Log(value = "新增租户", module = "租户管理")
    @PostMapping
    @SaCheckPermission("system:tenant:add")
    public R<Void> create(@Valid @RequestBody TenantSaveReq req) {
        tenantService.createTenant(req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "修改租户", module = "租户管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:tenant:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TenantSaveReq req) {
        tenantService.updateTenant(id, req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "删除租户", module = "租户管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:tenant:delete")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return R.ok();
    }
}
