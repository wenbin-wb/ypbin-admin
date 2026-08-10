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
import cn.ypbin.admin.system.model.query.RoleQuery;
import cn.ypbin.admin.system.model.req.RoleSaveReq;
import cn.ypbin.admin.system.model.req.StatusReq;
import cn.ypbin.admin.system.model.resp.RoleResp;
import cn.ypbin.admin.system.service.SysRoleService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageResult;
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
 * 角色管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController extends BaseController {

    private final SysRoleService roleService;

    @GetMapping("/list")
    @SaCheckPermission("system:role:list")
    public R<PageResult<RoleResp>> list(@Valid RoleQuery query) {
        return ok(roleService.pageRoles(query));
    }

    @GetMapping("/all")
    @SaCheckPermission("system:role:list")
    public R<List<RoleResp>> all() {
        return ok(roleService.listAll());
    }

    @Log(value = "新增角色", module = "角色管理")
    @PostMapping
    @SaCheckPermission("system:role:add")
    public R<Void> create(@Valid @RequestBody RoleSaveReq req) {
        roleService.createRole(req);
        return ok();
    }

    @Log(value = "修改角色", module = "角色管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:role:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveReq req) {
        roleService.updateRole(id, req);
        return ok();
    }

    @Log(value = "修改角色状态", module = "角色管理")
    @PutMapping("/{id}/status")
    @SaCheckPermission("system:role:edit")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusReq req) {
        roleService.updateStatus(id, req.getStatus());
        return ok();
    }

    @Log(value = "删除角色", module = "角色管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:role:delete")
    public R<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ok();
    }
}
