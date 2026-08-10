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
import cn.ypbin.admin.system.model.req.AuthTemplateSaveReq;
import cn.ypbin.admin.system.model.resp.AuthTemplateResp;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
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
 * 权限模板管理接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/system/auth-template")
@RequiredArgsConstructor
@PlatformAccess
public class SysAuthTemplateController extends BaseController {

    private final SysAuthTemplateService templateService;

    @GetMapping("/list")
    @SaCheckPermission("system:auth-template:list")
    public R<List<AuthTemplateResp>> list() {
        return ok(templateService.listTemplates());
    }

    @Log(value = "新增权限模板", module = "权限模板")
    @PostMapping
    @SaCheckPermission("system:auth-template:add")
    public R<Void> create(@Valid @RequestBody AuthTemplateSaveReq req) {
        templateService.createTemplate(req);
        return ok();
    }

    @Log(value = "修改权限模板", module = "权限模板")
    @PutMapping("/{id}")
    @SaCheckPermission("system:auth-template:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AuthTemplateSaveReq req) {
        templateService.updateTemplate(id, req);
        return ok();
    }

    @Log(value = "删除权限模板", module = "权限模板")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:auth-template:delete")
    public R<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ok();
    }
}
