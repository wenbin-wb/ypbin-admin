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
import cn.ypbin.admin.system.model.resp.JobLogResp;
import cn.ypbin.admin.system.service.SysJobService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.job.core.JobDefinition;
import cn.ypbin.starter.job.core.JobManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    @Override
    public PageResult<JobLogResp> pageLogs(Long jobId, PageQuery query) {
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
            .eq(jobId != null, SysJobLog::getJobId, jobId)
            .orderByDesc(SysJobLog::getCreateTime);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysJobLog> mpPage =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(query.getPage(), query.getPageSize());
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysJobLog> result =
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
