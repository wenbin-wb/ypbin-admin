/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysJob;
import cn.ypbin.admin.system.model.req.JobSaveReq;
import cn.ypbin.admin.system.model.resp.CronPreviewResp;
import cn.ypbin.admin.system.model.resp.JobLogResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.service.BaseService;

/**
 * 定时任务服务。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface SysJobService extends BaseService<SysJob> {

    /**
     * 新增任务。
     *
     * @param req 任务参数
     */
    void createJob(JobSaveReq req);

    /**
     * 修改任务。
     *
     * @param id 任务 ID
     * @param req 任务参数
     */
    void updateJob(Long id, JobSaveReq req);

    /**
     * 校验 Cron 并预览后续执行时间。
     *
     * @param cron Cron 表达式
     * @return 预览结果
     */
    CronPreviewResp previewCron(String cron);

    /**
     * 分页查询执行日志。
     *
     * @param jobId 任务 ID，为空查全部
     * @param query 分页参数
     * @return 分页结果
     */
    PageResult<JobLogResp> pageLogs(Long jobId, PageQuery query);

    /**
     * 启动调度（注册到 JobManager）。
     *
     * @param id 任务 ID
     */
    void start(Long id);

    /**
     * 停止调度（从 JobManager 移除）。
     *
     * @param id 任务 ID
     */
    void stop(Long id);

    /**
     * 立即执行一次。
     *
     * @param id 任务 ID
     */
    void triggerNow(Long id);
}
