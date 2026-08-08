/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.entity.SysJob;
import cn.ypbin.admin.system.entity.SysJobLog;
import cn.ypbin.admin.system.mapper.SysJobLogMapper;
import cn.ypbin.admin.system.mapper.SysJobMapper;
import cn.ypbin.admin.system.model.req.JobSaveReq;
import cn.ypbin.admin.system.model.resp.CronPreviewResp;
import cn.ypbin.admin.system.model.resp.JobLogResp;
import cn.ypbin.admin.system.service.SysJobService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.job.core.CronService;
import cn.ypbin.starter.job.core.JobDefinition;
import cn.ypbin.starter.job.core.JobManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 定时任务服务实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SysJobServiceImpl extends BaseServiceImpl<SysJobMapper, SysJob> implements SysJobService {

    private final SysJobLogMapper jobLogMapper;
    private final JobManager jobManager;
    private final CronService cronService;

    @Override
    public void createJob(JobSaveReq req) {
        validateCron(req.getCron());
        SysJob job = new SysJob();
        BeanUtils.copyProperties(req, job);
        job.setStatus(0);
        save(job);
    }

    @Override
    public void updateJob(Long id, JobSaveReq req) {
        validateCron(req.getCron());
        SysJob existing = requireJob(id);
        BeanUtils.copyProperties(req, existing);
        updateById(existing);
        if (existing.getStatus() != null && existing.getStatus() == 1) {
            jobManager.register(toDefinition(existing));
        }
    }

    @Override
    public CronPreviewResp previewCron(String cron) {
        ZoneId zoneId = ZoneId.systemDefault();
        try {
            List<ZonedDateTime> nextExecutionTimes =
                cronService.nextExecutionTimes(cron, ZonedDateTime.now(zoneId), 5);
            return new CronPreviewResp(true, "Cron 表达式合法", zoneId.getId(), nextExecutionTimes);
        } catch (IllegalArgumentException e) {
            return new CronPreviewResp(false, e.getMessage(), zoneId.getId(), List.of());
        }
    }

    @Override
    public PageResult<JobLogResp> pageLogs(Long jobId, PageQuery query) {
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
            .eq(jobId != null, SysJobLog::getJobId, jobId)
            .orderByDesc(SysJobLog::getCreateTime);
        Page<SysJobLog> mpPage =
            new Page<>(query.getPage(), query.getPageSize());
        Page<SysJobLog> result =
            jobLogMapper.selectPage(mpPage, wrapper);
        List<JobLogResp> items = result.getRecords().stream().map(log -> {
            JobLogResp r = new JobLogResp();
            BeanUtils.copyProperties(log, r);
            return r;
        }).toList();
        return PageResult.of(items, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void start(Long id) {
        SysJob job = requireJob(id);
        job.setStatus(1);
        updateById(job);
        jobManager.register(toDefinition(job));
    }

    @Override
    public void stop(Long id) {
        SysJob job = requireJob(id);
        job.setStatus(0);
        updateById(job);
        jobManager.unregister(id);
    }

    @Override
    public void triggerNow(Long id) {
        SysJob job = requireJob(id);
        jobManager.triggerNow(toDefinition(job));
    }

    private void validateCron(String cron) {
        try {
            cronService.validate(cron);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    private SysJob requireJob(Long id) {
        SysJob job = getById(id);
        if (job == null) {
            throw new BusinessException("任务不存在");
        }
        return job;
    }

    private JobDefinition toDefinition(SysJob job) {
        JobDefinition def = new JobDefinition(job.getId(), job.getName(), job.getExecutor(), job.getCron());
        def.setFixedRateSeconds(job.getFixedRateSeconds());
        def.setArgs(job.getArgs());
        def.setTimeoutSeconds(job.getTimeoutSeconds() != null ? job.getTimeoutSeconds() : 0);
        def.setConcurrentGuard(job.getConcurrentGuard() == null || job.getConcurrentGuard() == 1);
        return def;
    }
}
