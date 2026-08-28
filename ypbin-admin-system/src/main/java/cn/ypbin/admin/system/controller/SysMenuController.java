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
import cn.ypbin.admin.system.model.req.MenuSaveReq;
import cn.ypbin.admin.system.model.resp.MenuResp;
import cn.ypbin.admin.system.service.SysMenuService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
@PlatformAccess
public class SysMenuController extends BaseController {

    private final SysMenuService menuService;

    @GetMapping("/list")
    @SaCheckPermission("system:menu:list")
    public R<List<MenuResp>> list() {
        return ok(menuService.tree());
    }

    @GetMapping("/name-exists")
    @SaCheckPermission("system:menu:list")
    public R<Boolean> nameExists(@RequestParam String name, @RequestParam(required = false) Long id) {
        return ok(menuService.isNameExists(name, id));
    }

    @GetMapping("/path-exists")
    @SaCheckPermission("system:menu:list")
    public R<Boolean> pathExists(@RequestParam String path, @RequestParam(required = false) Long id) {
        return ok(menuService.isPathExists(path, id));
    }

    @Idempotent
    @Log(value = "新增菜单", module = "菜单管理")
    @PostMapping
    @SaCheckPermission("system:menu:add")
    public R<Void> create(@Valid @RequestBody MenuSaveReq req) {
        menuService.createMenu(req);
        return ok();
    }

    @Idempotent
    @Log(value = "修改菜单", module = "菜单管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:menu:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MenuSaveReq req) {
        menuService.updateMenu(id, req);
        return ok();
    }

    @Idempotent
    @Log(value = "删除菜单", module = "菜单管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:menu:delete")
    public R<Void> delete(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ok();
    }
}
