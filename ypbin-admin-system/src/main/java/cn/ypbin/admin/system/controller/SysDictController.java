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
import cn.ypbin.admin.system.model.query.DictQuery;
import cn.ypbin.admin.system.model.req.DictSaveReq;
import cn.ypbin.admin.system.model.resp.DictResp;
import cn.ypbin.admin.system.service.SysDictService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.validation.Valid;
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
 * 字典类型管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
@PlatformAccess
public class SysDictController extends BaseController {

    private final SysDictService dictService;

    @GetMapping("/list")
    @SaCheckPermission("system:dict:list")
    public R<PageResult<DictResp>> list(@Valid DictQuery query) {
        return ok(dictService.pageDicts(query));
    }

    @Idempotent
    @Log(value = "新增字典", module = "字典管理")
    @PostMapping
    @SaCheckPermission("system:dict:add")
    public R<Void> create(@Valid @RequestBody DictSaveReq req) {
        dictService.createDict(req);
        return ok();
    }

    @Idempotent
    @Log(value = "修改字典", module = "字典管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:dict:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DictSaveReq req) {
        dictService.updateDict(id, req);
        return ok();
    }

    @Idempotent
    @Log(value = "删除字典", module = "字典管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:dict:delete")
    public R<Void> delete(@PathVariable Long id) {
        dictService.deleteDict(id);
        return ok();
    }
}
