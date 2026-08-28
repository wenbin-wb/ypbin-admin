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
import cn.ypbin.admin.system.model.req.SysClientSaveReq;
import cn.ypbin.admin.system.model.resp.ClientCredentialResp;
import cn.ypbin.admin.system.model.resp.ClientResp;
import cn.ypbin.admin.system.service.SysClientService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.log.annotation.Log;
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
 * 登录客户端管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/client")
@RequiredArgsConstructor
@PlatformAccess
public class SysClientController extends BaseController {

    private final SysClientService clientService;

    @GetMapping("/list")
    @SaCheckPermission("system:client:list")
    public R<List<ClientResp>> list() {
        return ok(clientService.listClients());
    }

    @Idempotent
    @Log(value = "新增客户端", module = "客户端管理")
    @PostMapping
    @SaCheckPermission("system:client:add")
    public R<ClientCredentialResp> create(@Valid @RequestBody SysClientSaveReq req) {
        return ok(clientService.createClient(req));
    }

    @Idempotent
    @Log(value = "重置客户端密钥", module = "客户端管理")
    @PutMapping("/{id}/reset-secret")
    @SaCheckPermission("system:client:reset-secret")
    public R<ClientCredentialResp> resetSecret(@PathVariable Long id) {
        return ok(clientService.resetSecret(id));
    }

    @Idempotent
    @Log(value = "修改客户端", module = "客户端管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:client:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysClientSaveReq req) {
        clientService.updateClient(id, req);
        return ok();
    }

    @Idempotent
    @Log(value = "删除客户端", module = "客户端管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:client:delete")
    public R<Void> delete(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ok();
    }
}
