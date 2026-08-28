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
import cn.ypbin.admin.system.model.req.DeptSaveReq;
import cn.ypbin.admin.system.model.resp.DeptResp;
import cn.ypbin.admin.system.service.SysDeptService;
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
 * 部门管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController extends BaseController {

    private final SysDeptService deptService;

    @GetMapping("/list")
    @SaCheckPermission("system:dept:list")
    public R<List<DeptResp>> list() {
        return ok(deptService.tree());
    }

    @Idempotent
    @Log(value = "新增部门", module = "部门管理")
    @PostMapping
    @SaCheckPermission("system:dept:add")
    public R<Void> create(@Valid @RequestBody DeptSaveReq req) {
        deptService.createDept(req);
        return ok();
    }

    @Idempotent
    @Log(value = "修改部门", module = "部门管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:dept:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DeptSaveReq req) {
        deptService.updateDept(id, req);
        return ok();
    }

    @Log(value = "删除部门", module = "部门管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:dept:delete")
    public R<Void> delete(@PathVariable Long id) {
        deptService.deleteDept(id);
        return ok();
    }
}
