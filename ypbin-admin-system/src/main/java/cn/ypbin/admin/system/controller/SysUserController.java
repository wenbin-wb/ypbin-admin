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
import cn.ypbin.admin.system.model.query.UserQuery;
import cn.ypbin.admin.system.model.req.AssignRolesReq;
import cn.ypbin.admin.system.model.req.ResetPasswordReq;
import cn.ypbin.admin.system.model.req.StatusReq;
import cn.ypbin.admin.system.model.req.UserSaveReq;
import cn.ypbin.admin.system.model.resp.UserResp;
import cn.ypbin.admin.system.model.vo.UserImportResult;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController extends BaseController {

    private final SysUserService userService;

    @GetMapping("/list")
    @SaCheckPermission("system:user:list")
    public R<PageResult<UserResp>> list(@Valid UserQuery query) {
        return ok(userService.pageUsers(query));
    }

    @GetMapping("/export")
    @SaCheckPermission("system:user:list")
    public void export(@Valid UserQuery query, HttpServletResponse response) {
        userService.exportUsers(query, response);
    }

    @GetMapping("/import-template")
    @SaCheckPermission("system:user:add")
    public void downloadImportTemplate(HttpServletResponse response) {
        userService.downloadImportTemplate(response);
    }

    @Idempotent
    @Log(value = "批量导入用户", module = "用户管理")
    @PostMapping("/import")
    @SaCheckPermission("system:user:add")
    public R<UserImportResult> importUsers(@RequestParam("file") MultipartFile file) {
        return ok(userService.importUsers(file));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:user:list")
    public R<UserResp> get(@PathVariable Long id) {
        return ok(userService.getUserDetail(id));
    }

    @Idempotent
    @Log(value = "新增用户", module = "用户管理")
    @PostMapping
    @SaCheckPermission("system:user:add")
    public R<Void> create(@Valid @RequestBody UserSaveReq req) {
        userService.createUser(req);
        return ok();
    }

    @Idempotent
    @Log(value = "修改用户", module = "用户管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:user:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveReq req) {
        userService.updateUser(id, req);
        return ok();
    }

    @Log(value = "修改用户状态", module = "用户管理")
    @PutMapping("/{id}/status")
    @SaCheckPermission("system:user:edit")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusReq req) {
        userService.updateStatus(id, req.getStatus());
        return ok();
    }

    @Log(value = "删除用户", module = "用户管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:user:delete")
    public R<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ok();
    }

    @Idempotent
    @Log(value = "重置用户密码", module = "用户管理")
    @PutMapping("/{id}/reset-password")
    @SaCheckPermission("system:user:edit")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordReq req) {
        userService.resetPassword(id, req.getPassword());
        return ok();
    }

    @Idempotent
    @Log(value = "分配用户角色", module = "用户管理")
    @PutMapping("/{id}/roles")
    @SaCheckPermission("system:user:edit")
    public R<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesReq req) {
        userService.assignRoles(id, req.getRoleIds());
        return ok();
    }
}
