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
import cn.ypbin.admin.system.model.req.DictItemSaveReq;
import cn.ypbin.admin.system.model.resp.DictItemResp;
import cn.ypbin.admin.system.service.SysDictItemService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictUtils;
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
 * 字典项管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/dict-item")
@RequiredArgsConstructor
@PlatformAccess
public class SysDictItemController extends BaseController {

    private final SysDictItemService dictItemService;

    @GetMapping("/list")
    @SaCheckPermission("system:dict:list")
    public R<List<DictItemResp>> list(@RequestParam Long dictId) {
        return ok(dictItemService.listByDictId(dictId));
    }

    /**
     * 按字典编码查询字典项（下拉/标签渲染用），仅需登录，走 starter 字典缓存。
     */
    @GetMapping("/options/{dictCode}")
    public R<List<DictItem>> options(@PathVariable String dictCode) {
        return ok(DictUtils.getItems(dictCode));
    }

    @Idempotent
    @Log(value = "新增字典项", module = "字典管理")
    @PostMapping
    @SaCheckPermission("system:dict:add")
    public R<Void> create(@Valid @RequestBody DictItemSaveReq req) {
        dictItemService.createItem(req);
        return ok();
    }

    @Idempotent
    @Log(value = "修改字典项", module = "字典管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:dict:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DictItemSaveReq req) {
        dictItemService.updateItem(id, req);
        return ok();
    }

    @Idempotent
    @Log(value = "删除字典项", module = "字典管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:dict:delete")
    public R<Void> delete(@PathVariable Long id) {
        dictItemService.deleteItem(id);
        return ok();
    }
}
