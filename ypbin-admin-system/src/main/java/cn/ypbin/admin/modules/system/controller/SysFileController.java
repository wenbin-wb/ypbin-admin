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
import cn.ypbin.admin.modules.system.entity.SysFile;
import cn.ypbin.admin.modules.system.model.query.FileQuery;
import cn.ypbin.admin.modules.system.service.SysFileService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.storage.model.FileInfo;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件管理接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/system/file")
@RequiredArgsConstructor
@PlatformAccess
public class SysFileController {

    private final SysFileService fileService;

    @Idempotent
    @Log(value = "上传文件", module = "文件管理")
    @PostMapping("/upload")
    @SaCheckPermission("system:file:upload")
    public R<FileInfo> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "default") String module) {
        return R.ok(fileService.uploadFile(file, module));
    }

    @GetMapping("/list")
    @SaCheckPermission("system:file:list")
    public R<PageResult<SysFile>> list(@Valid FileQuery query) {
        return R.ok(fileService.pageFiles(query));
    }

    @Idempotent
    @Log(value = "删除文件", module = "文件管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:file:delete")
    public R<Void> delete(@PathVariable Long id) {
        fileService.deleteFile(id);
        return R.ok();
    }
}
