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
import cn.ypbin.admin.modules.system.annotation.PlatformAccess;
import cn.ypbin.admin.modules.system.model.query.ConfigQuery;
import cn.ypbin.admin.modules.system.model.req.ConfigSaveReq;
import cn.ypbin.admin.modules.system.model.req.ConfigUpdateBatchReq;
import cn.ypbin.admin.modules.system.model.resp.ConfigResp;
import cn.ypbin.admin.modules.system.service.SysConfigService;
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
 * 系统参数管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
@PlatformAccess
public class SysConfigController {

    private final SysConfigService configService;

    @GetMapping("/list")
    @SaCheckPermission("system:config:list")
    public R<PageResult<ConfigResp>> list(@Valid ConfigQuery query) {
        return R.ok(configService.pageConfigs(query));
    }

    @GetMapping("/group/{configGroup}")
    @SaCheckPermission("system:config:list")
    public R<List<ConfigResp>> group(@PathVariable String configGroup) {
        return R.ok(configService.listByGroup(configGroup));
    }

    @Idempotent
    @Log(value = "新增参数", module = "系统参数")
    @PostMapping
    @SaCheckPermission("system:config:add")
    public R<Void> create(@Valid @RequestBody ConfigSaveReq req) {
        configService.createConfig(req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "修改参数", module = "系统参数")
    @PutMapping("/{id}")
    @SaCheckPermission("system:config:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ConfigSaveReq req) {
        configService.updateConfig(id, req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "批量保存参数", module = "系统参数")
    @PutMapping("/group/{configGroup}")
    @SaCheckPermission("system:config:edit")
    public R<Void> updateGroup(@PathVariable String configGroup,
                               @Valid @RequestBody ConfigUpdateBatchReq req) {
        configService.updateGroup(configGroup, req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "删除参数", module = "系统参数")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:config:delete")
    public R<Void> delete(@PathVariable Long id) {
        configService.deleteConfig(id);
        return R.ok();
    }
}
