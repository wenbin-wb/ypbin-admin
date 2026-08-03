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
import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import cn.ypbin.starter.log.annotation.Log;
import java.time.LocalDateTime;
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
 * 公告管理接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class SysNoticeController extends BaseController {

    private final BaseService<SysNotice> noticeService;

    @GetMapping("/list")
    @SaCheckPermission("system:notice:list")
    public R<List<SysNotice>> list() {
        return ok(noticeService.list());
    }

    @Log(value = "新增公告", module = "公告管理")
    @PostMapping
    @SaCheckPermission("system:notice:add")
    public R<Void> create(@RequestBody SysNotice entity) {
        applyPublishState(entity);
        noticeService.save(entity);
        return ok();
    }

    @Log(value = "修改公告", module = "公告管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:notice:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody SysNotice entity) {
        entity.setId(id);
        applyPublishState(entity);
        noticeService.updateById(entity);
        return ok();
    }

    @Log(value = "撤回公告", module = "公告管理")
    @PutMapping("/{id}/revoke")
    @SaCheckPermission("system:notice:edit")
    public R<Void> revoke(@PathVariable Long id) {
        SysNotice entity = new SysNotice();
        entity.setId(id);
        entity.setPublishStatus(3);
        noticeService.updateById(entity);
        return ok();
    }

    /**
     * 依据发布方式推导发布状态与发布时间：
     * 草稿(publishStatus=0) 保持草稿；定时(publishType=2) 置为待发布；否则立即发布。
     */
    private void applyPublishState(SysNotice entity) {
        Integer publishStatus = entity.getPublishStatus();
        if (publishStatus != null && publishStatus == 0) {
            return;
        }
        if (entity.getPublishType() != null && entity.getPublishType() == 2) {
            entity.setPublishStatus(1);
        } else {
            entity.setPublishStatus(2);
            entity.setPublishTime(LocalDateTime.now());
        }
    }

    @Log(value = "删除公告", module = "公告管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:notice:delete")
    public R<Void> delete(@PathVariable Long id) {
        noticeService.removeById(id);
        return ok();
    }
}
