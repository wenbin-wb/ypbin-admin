/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.ai.model.req.AiChatRoleSaveReq;
import cn.ypbin.admin.system.ai.model.resp.AiChatRoleResp;
import cn.ypbin.admin.system.ai.service.AiChatRoleService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.tools.idempotent.Idempotent;
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
 * AI 对话角色接口。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@RestController
@RequestMapping("/ai/roles")
@RequiredArgsConstructor
public class AiChatRoleController extends BaseController {

    private final AiChatRoleService roleService;

    @GetMapping
    @SaCheckPermission("ai:role:list")
    public R<List<AiChatRoleResp>> listRoles() {
        return ok(roleService.listRoles());
    }

    @Idempotent
    @PostMapping
    @SaCheckPermission("ai:role:create")
    @Log(value = "创建自定义角色", module = "AI 角色")
    public R<Long> createRole(@Valid @RequestBody AiChatRoleSaveReq req) {
        return ok(roleService.createRole(req));
    }

    @Idempotent
    @PutMapping("/{id}")
    @SaCheckPermission("ai:role:edit")
    @Log(value = "修改角色", module = "AI 角色")
    public R<Void> updateRole(@PathVariable Long id, @Valid @RequestBody AiChatRoleSaveReq req) {
        roleService.updateRole(id, req);
        return ok();
    }

    @Idempotent
    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:role:delete")
    @Log(value = "删除角色", module = "AI 角色")
    public R<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ok();
    }

    @Idempotent
    @PutMapping("/{id}/favorite")
    @SaCheckPermission("ai:role:list")
    @Log(value = "收藏/取消收藏角色", module = "AI 角色")
    public R<Void> toggleFavorite(@PathVariable Long id) {
        roleService.toggleFavorite(id);
        return ok();
    }
}
