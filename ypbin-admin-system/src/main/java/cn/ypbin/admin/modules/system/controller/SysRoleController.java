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
import cn.ypbin.admin.modules.system.model.query.RoleQuery;
import cn.ypbin.admin.modules.system.model.req.RoleSaveReq;
import cn.ypbin.admin.modules.system.model.req.StatusReq;
import cn.ypbin.admin.modules.system.model.resp.MenuResp;
import cn.ypbin.admin.modules.system.model.resp.RoleResp;
import cn.ypbin.admin.modules.system.service.SysMenuService;
import cn.ypbin.admin.modules.system.service.SysRoleService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageResult;
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
 * 角色管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    private final SysMenuService menuService;

    /**
     * 当前租户可授权的菜单树（角色授权弹窗使用）。
     *
     * <p>放在角色接口而非菜单接口：角色管理对租户管理员开放，而菜单管理是平台专用；
     * 返回的节点带 {@code assignable}，仅为保持树结构保留的祖先不可勾选。</p>
     *
     * @return 可授权菜单树
     */
    @GetMapping("/assignable-menus")
    @SaCheckPermission("system:role:list")
    public R<List<MenuResp>> assignableMenus() {
        return R.ok(menuService.assignableTree());
    }

    @GetMapping("/list")
    @SaCheckPermission("system:role:list")
    public R<PageResult<RoleResp>> list(@Valid RoleQuery query) {
        return R.ok(roleService.pageRoles(query));
    }

    @GetMapping("/all")
    @SaCheckPermission("system:role:list")
    public R<List<RoleResp>> all() {
        return R.ok(roleService.listAll());
    }

    @Idempotent
    @Log(value = "新增角色", module = "角色管理")
    @PostMapping
    @SaCheckPermission("system:role:add")
    public R<Void> create(@Valid @RequestBody RoleSaveReq req) {
        roleService.createRole(req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "修改角色", module = "角色管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:role:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveReq req) {
        roleService.updateRole(id, req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "修改角色状态", module = "角色管理")
    @PutMapping("/{id}/status")
    @SaCheckPermission("system:role:edit")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusReq req) {
        roleService.updateStatus(id, req.getStatus());
        return R.ok();
    }

    @Idempotent
    @Log(value = "删除角色", module = "角色管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:role:delete")
    public R<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return R.ok();
    }
}
