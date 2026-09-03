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
import cn.ypbin.admin.modules.system.model.req.PostSaveReq;
import cn.ypbin.admin.modules.system.model.resp.PostResp;
import cn.ypbin.admin.modules.system.service.SysPostService;
import cn.ypbin.starter.core.model.R;
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
 * 岗位管理接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService postService;

    @GetMapping("/list")
    @SaCheckPermission("system:post:list")
    public R<List<PostResp>> list() {
        return R.ok(postService.listPosts());
    }

    @Idempotent
    @Log(value = "新增岗位", module = "岗位管理")
    @PostMapping
    @SaCheckPermission("system:post:add")
    public R<Void> create(@Valid @RequestBody PostSaveReq req) {
        postService.createPost(req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "修改岗位", module = "岗位管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:post:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PostSaveReq req) {
        postService.updatePost(id, req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "删除岗位", module = "岗位管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:post:delete")
    public R<Void> delete(@PathVariable Long id) {
        postService.deletePost(id);
        return R.ok();
    }
}
