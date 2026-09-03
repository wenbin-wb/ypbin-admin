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
import cn.ypbin.admin.modules.system.entity.SysJob;
import cn.ypbin.admin.modules.system.model.req.CronPreviewReq;
import cn.ypbin.admin.modules.system.model.req.JobSaveReq;
import cn.ypbin.admin.modules.system.model.resp.CronPreviewResp;
import cn.ypbin.admin.modules.system.model.resp.JobLogResp;
import cn.ypbin.admin.modules.system.service.SysJobService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageQuery;
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
 * 定时任务管理接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/system/job")
@RequiredArgsConstructor
@PlatformAccess
public class SysJobController {

    private final SysJobService jobService;

    @GetMapping("/list")
    @SaCheckPermission("system:job:list")
    public R<List<SysJob>> list() {
        return R.ok(jobService.list());
    }

    @GetMapping("/log")
    @SaCheckPermission("system:job:list")
    public R<PageResult<JobLogResp>> allLogs(@Valid PageQuery query) {
        return R.ok(jobService.pageLogs(null, query));
    }

    @GetMapping("/log/{jobId}")
    @SaCheckPermission("system:job:list")
    public R<PageResult<JobLogResp>> logs(@PathVariable Long jobId, @Valid PageQuery query) {
        return R.ok(jobService.pageLogs(jobId, query));
    }

    @PostMapping("/cron/preview")
    @SaCheckPermission("system:job:list")
    public R<CronPreviewResp> previewCron(@Valid @RequestBody CronPreviewReq req) {
        return R.ok(jobService.previewCron(req.getCron()));
    }

    @Idempotent
    @Log(value = "新增定时任务", module = "定时任务")
    @PostMapping
    @SaCheckPermission("system:job:add")
    public R<Void> create(@Valid @RequestBody JobSaveReq req) {
        jobService.createJob(req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "修改定时任务", module = "定时任务")
    @PutMapping("/{id}")
    @SaCheckPermission("system:job:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody JobSaveReq req) {
        jobService.updateJob(id, req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "删除定时任务", module = "定时任务")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:job:delete")
    public R<Void> delete(@PathVariable Long id) {
        jobService.deleteJob(id);
        return R.ok();
    }

    @Idempotent
    @Log(value = "启动定时任务", module = "定时任务")
    @PostMapping("/{id}/start")
    @SaCheckPermission("system:job:edit")
    public R<Void> start(@PathVariable Long id) {
        jobService.start(id);
        return R.ok();
    }

    @Idempotent
    @Log(value = "停止定时任务", module = "定时任务")
    @PostMapping("/{id}/stop")
    @SaCheckPermission("system:job:edit")
    public R<Void> stop(@PathVariable Long id) {
        jobService.stop(id);
        return R.ok();
    }

    @Idempotent
    @Log(value = "执行定时任务", module = "定时任务")
    @PostMapping("/{id}/run")
    @SaCheckPermission("system:job:edit")
    public R<Void> run(@PathVariable Long id) {
        jobService.triggerNow(id);
        return R.ok();
    }
}
