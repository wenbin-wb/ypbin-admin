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

import cn.ypbin.admin.system.entity.SysFile;
import cn.ypbin.admin.system.service.SysFileService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.storage.model.FileInfo;
import cn.ypbin.starter.storage.core.FileStorageService;
import java.io.IOException;
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
public class SysFileController extends BaseController {

    private final FileStorageService fileStorageService;
    private final SysFileService fileService;

    @Log(value = "上传文件", module = "文件管理")
    @PostMapping("/upload")
    public R<FileInfo> upload(@RequestParam("file") MultipartFile file,
                              @RequestParam(defaultValue = "default") String module) {
        try {
            FileInfo info = fileStorageService.upload(file.getInputStream(), file.getOriginalFilename())
                .path(module + "/")
                .execute();
            SysFile entity = new SysFile();
            entity.setPlatform(info.getPlatform());
            entity.setUrl(info.getUrl());
            entity.setOriginalName(info.getOriginalName());
            entity.setFileName(info.getFileName());
            entity.setFileSize(info.getSize());
            entity.setContentType(info.getContentType());
            entity.setExtension(info.getExtension());
            entity.setHash(info.getHash());
            entity.setModule(module);
            fileService.save(entity);
            return ok(info);
        } catch (IOException e) {
            return R.fail(500, "文件上传失败：" + e.getMessage());
        }
    }

    @GetMapping("/list")
    public R<PageResult<SysFile>> list(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long pageSize) {
        // thin wrapper — delegate to paged list
        return ok(fileService.page(new cn.ypbin.starter.crud.model.PageQuery() {{
            setPage(page);
            setPageSize(pageSize);
        }}));
    }

    @Log(value = "删除文件", module = "文件管理")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        fileService.removeById(id);
        return ok();
    }
}
