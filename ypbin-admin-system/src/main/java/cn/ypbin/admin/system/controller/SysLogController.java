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
import cn.ypbin.admin.system.model.query.LogQuery;
import cn.ypbin.admin.system.model.resp.LogResp;
import cn.ypbin.admin.system.service.SysLogService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统日志查询接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/log")
@RequiredArgsConstructor
@SaCheckPermission("system:log:list")
@PlatformAccess
public class SysLogController extends BaseController {

    private final SysLogService logService;

    @GetMapping("/list")
    public R<PageResult<LogResp>> list(@Valid LogQuery query) {
        return ok(logService.pageLogs(query));
    }
}
