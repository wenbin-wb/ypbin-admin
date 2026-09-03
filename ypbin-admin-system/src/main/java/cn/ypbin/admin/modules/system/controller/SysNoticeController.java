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
import cn.ypbin.admin.modules.system.entity.SysNotice;
import cn.ypbin.admin.modules.system.model.req.NoticeSaveReq;
import cn.ypbin.admin.modules.system.service.SysNoticeService;
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
 * 公告管理接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService noticeService;

    @GetMapping("/list")
    @SaCheckPermission("system:notice:list")
    public R<List<SysNotice>> list() {
        return R.ok(noticeService.listNotices());
    }

    @Idempotent
    @Log(value = "新增公告", module = "公告管理")
    @PostMapping
    @SaCheckPermission("system:notice:add")
    public R<Void> create(@Valid @RequestBody NoticeSaveReq req) {
        noticeService.createNotice(req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "修改公告", module = "公告管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:notice:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody NoticeSaveReq req) {
        noticeService.updateNotice(id, req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "撤回公告", module = "公告管理")
    @PutMapping("/{id}/revoke")
    @SaCheckPermission("system:notice:edit")
    public R<Void> revoke(@PathVariable Long id) {
        noticeService.revoke(id);
        return R.ok();
    }

    @Idempotent
    @Log(value = "发布公告", module = "公告管理")
    @PutMapping("/{id}/publish")
    @SaCheckPermission("system:notice:edit")
    public R<Void> publish(@PathVariable Long id) {
        noticeService.publish(id);
        return R.ok();
    }

    @Idempotent
    @Log(value = "删除公告", module = "公告管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:notice:delete")
    public R<Void> delete(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return R.ok();
    }
}
