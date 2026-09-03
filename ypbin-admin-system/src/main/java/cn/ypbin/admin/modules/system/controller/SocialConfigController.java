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
import cn.ypbin.admin.modules.system.model.req.SocialConfigUpdateReq;
import cn.ypbin.admin.modules.system.model.resp.SocialConfigResp;
import cn.ypbin.admin.modules.system.service.SocialConfigService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方登录平台配置管理接口。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/system/config/social")
@RequiredArgsConstructor
@PlatformAccess
public class SocialConfigController {

    private final SocialConfigService socialConfigService;

    @GetMapping
    @SaCheckPermission("system:config:list")
    public R<List<SocialConfigResp>> list() {
        return R.ok(socialConfigService.listConfigs());
    }

    @GetMapping("/{source}")
    @SaCheckPermission("system:config:list")
    public R<SocialConfigResp> get(@PathVariable String source) {
        return R.ok(socialConfigService.getConfig(source));
    }

    @Idempotent
    @Log(value = "修改第三方登录配置", module = "系统参数",
        excludes = {Include.REQUEST_PARAM, Include.REQUEST_BODY})
    @PutMapping("/{source}")
    @SaCheckPermission("system:config:edit")
    public R<Void> update(@PathVariable String source,
                          @Valid @RequestBody SocialConfigUpdateReq req) {
        socialConfigService.updateConfig(source, req);
        return R.ok();
    }
}
