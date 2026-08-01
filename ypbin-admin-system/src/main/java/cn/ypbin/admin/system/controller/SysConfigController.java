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
import cn.ypbin.admin.system.model.query.ConfigQuery;
import cn.ypbin.admin.system.model.req.ConfigSaveReq;
import cn.ypbin.admin.system.model.req.ConfigUpdateBatchReq;
import cn.ypbin.admin.system.model.resp.ConfigResp;
import cn.ypbin.admin.system.service.SysConfigService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统参数管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController extends BaseController {

    private final SysConfigService configService;

    @GetMapping("/list")
    @SaCheckPermission("system:config:list")
    public R<PageResult<ConfigResp>> list(ConfigQuery query) {
        return ok(configService.pageConfigs(query));
    }

    @GetMapping("/group/{configGroup}")
    @SaCheckPermission("system:config:list")
    public R<List<ConfigResp>> group(@PathVariable String configGroup) {
        return ok(configService.listByGroup(configGroup));
    }

    @Log(value = "新增参数", module = "系统参数")
    @PostMapping
    @SaCheckPermission("system:config:add")
    public R<Void> create(@Valid @RequestBody ConfigSaveReq req) {
        configService.createConfig(req);
        return ok();
    }

    @Log(value = "修改参数", module = "系统参数")
    @PutMapping("/{id}")
    @SaCheckPermission("system:config:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ConfigSaveReq req) {
        configService.updateConfig(id, req);
        return ok();
    }

    @Log(value = "批量保存参数", module = "系统参数")
    @PutMapping("/batch")
    @SaCheckPermission("system:config:edit")
    public R<Void> updateBatch(@RequestBody ConfigUpdateBatchReq req) {
        configService.updateBatch(req);
        return ok();
    }

    @Log(value = "删除参数", module = "系统参数")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:config:delete")
    public R<Void> delete(@PathVariable Long id) {
        configService.deleteConfig(id);
        return ok();
    }
}
