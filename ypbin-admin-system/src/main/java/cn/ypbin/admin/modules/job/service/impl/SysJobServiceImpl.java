/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.job.service.impl;

import cn.ypbin.admin.modules.job.entity.SysJob;
import cn.ypbin.admin.modules.job.entity.SysJobLog;
import cn.ypbin.admin.modules.job.enums.JobStatusEnum;
import cn.ypbin.admin.modules.job.mapper.SysJobLogMapper;
import cn.ypbin.admin.modules.job.mapper.SysJobMapper;
import cn.ypbin.admin.modules.job.model.req.JobSaveReq;
import cn.ypbin.admin.modules.job.model.resp.CronPreviewResp;
import cn.ypbin.admin.modules.job.model.resp.JobLogResp;
import cn.ypbin.admin.modules.job.service.SysJobService;
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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(rollbackFor = Exception.class)
    public synchronized void createJob(JobSaveReq req) {
        SysJob job = new SysJob();
        BeanUtils.copyProperties(req, job);
        validateDefinition(job);
        job.setStatus(JobStatusEnum.STOPPED.getCode());
        if (!save(job)) {
            throw new BusinessException("新增任务失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void updateJob(Long id, JobSaveReq req) {
        SysJob existing = requireJob(id);
        boolean previouslyScheduled = isEnabled(existing);
        JobDefinition previousDefinition = toDefinition(existing);
        BeanUtils.copyProperties(req, existing);
        validateDefinition(existing);
        JobDefinition updatedDefinition = toDefinition(existing);
        if (previouslyScheduled) {
            replaceRuntime(updatedDefinition);
        }
        try {
            if (!updateById(existing)) {
                throw new BusinessException("修改任务失败");
            }
        } catch (RuntimeException failure) {
            if (previouslyScheduled) {
                restoreRuntime(true, previousDefinition, failure);
            }
            throw failure;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void deleteJob(Long id) {
        SysJob job = requireJob(id);
        boolean previouslyScheduled = isEnabled(job);
        JobDefinition previousDefinition = toDefinition(job);
        jobManager.unregister(id);
        try {
            if (!removeById(id)) {
                throw new BusinessException("删除任务失败");
            }
        } catch (RuntimeException failure) {
            restoreRuntime(previouslyScheduled, previousDefinition, failure);
            throw failure;
        }
    }

    @Override
    public synchronized void reconcileRuntime() {
        List<SysJob> jobs = list(new LambdaQueryWrapper<SysJob>().eq(SysJob::getStatus, JobStatusEnum.ENABLED.getCode()));
        jobs.forEach(this::validateDefinition);
        List<JobDefinition> definitions = jobs.stream().map(this::toDefinition).toList();
        reconcileRuntime(definitions);
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
    @Transactional(rollbackFor = Exception.class)
    public synchronized void start(Long id) {
        SysJob job = requireJob(id);
        boolean previouslyScheduled = isEnabled(job);
        JobDefinition definition = toDefinition(job);
        jobManager.replace(definition);
        job.setStatus(JobStatusEnum.ENABLED.getCode());
        try {
            if (!updateById(job)) {
                throw new BusinessException("启动任务失败");
            }
        } catch (RuntimeException failure) {
            restoreRuntime(previouslyScheduled, definition, failure);
            throw failure;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void stop(Long id) {
        SysJob job = requireJob(id);
        boolean previouslyScheduled = isEnabled(job);
        JobDefinition definition = toDefinition(job);
        jobManager.unregister(id);
        job.setStatus(JobStatusEnum.STOPPED.getCode());
        try {
            if (!updateById(job)) {
                throw new BusinessException("停止任务失败");
            }
        } catch (RuntimeException failure) {
            restoreRuntime(previouslyScheduled, definition, failure);
            throw failure;
        }
    }

    @Override
    public void triggerNow(Long id) {
        SysJob job = requireJob(id);
        jobManager.triggerNow(toDefinition(job));
    }

    private void validateDefinition(SysJob job) {
        JobDefinition definition = toDefinition(job);
        try {
            jobManager.validateDefinition(definition);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    private void replaceRuntime(JobDefinition definition) {
        try {
            jobManager.replace(definition);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    private void reconcileRuntime(List<JobDefinition> definitions) {
        try {
            jobManager.reconcile(definitions);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
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

    private boolean isEnabled(SysJob job) {
        return JobStatusEnum.ENABLED.getCode().equals(job.getStatus());
    }

    private void restoreRuntime(boolean previouslyScheduled, JobDefinition definition,
                                RuntimeException failure) {
        try {
            if (previouslyScheduled) {
                jobManager.replace(definition);
            } else {
                jobManager.unregister(definition.getId());
            }
        } catch (RuntimeException compensationFailure) {
            failure.addSuppressed(compensationFailure);
        }
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
