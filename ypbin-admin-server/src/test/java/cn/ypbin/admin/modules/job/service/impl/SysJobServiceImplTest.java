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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cn.ypbin.admin.modules.job.entity.SysJob;
import cn.ypbin.admin.modules.job.mapper.SysJobLogMapper;
import cn.ypbin.admin.modules.job.mapper.SysJobMapper;
import cn.ypbin.admin.modules.job.model.req.JobSaveReq;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.job.core.CronService;
import cn.ypbin.starter.job.core.JobDefinition;
import cn.ypbin.starter.job.core.JobManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SysJobServiceImpl} 运行态补偿测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class SysJobServiceImplTest {

    private JobManager jobManager;
    private SysJobServiceImpl service;

    @BeforeEach
    void setUp() {
        jobManager = mock(JobManager.class);
        CronService cronService = mock(CronService.class);
        service = spy(new SysJobServiceImpl(
            mock(SysJobLogMapper.class), jobManager, cronService));
    }

    @Test
    void updateFailureRestoresPreviousDefinition() {
        SysJob job = job(1L, 1, "0 0 * * * *");
        doReturn(job).when(service).getById(1L);
        doReturn(false).when(service).updateById(any(SysJob.class));
        JobSaveReq req = request("0 30 * * * *");

        assertThatThrownBy(() -> service.updateJob(1L, req))
            .isInstanceOf(BusinessException.class)
            .hasMessage("修改任务失败");

        verify(jobManager, times(2)).replace(any(JobDefinition.class));
    }

    @Test
    void startFailureRemovesCandidateSchedule() {
        SysJob job = job(2L, 0, "0 0 * * * *");
        doReturn(job).when(service).getById(2L);
        doReturn(false).when(service).updateById(any(SysJob.class));

        assertThatThrownBy(() -> service.start(2L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("启动任务失败");

        verify(jobManager).replace(any(JobDefinition.class));
        verify(jobManager).unregister(2L);
    }

    @Test
    void stopFailureRestoresPreviousSchedule() {
        SysJob job = job(3L, 1, "0 0 * * * *");
        doReturn(job).when(service).getById(3L);
        doReturn(false).when(service).updateById(any(SysJob.class));

        assertThatThrownBy(() -> service.stop(3L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("停止任务失败");

        verify(jobManager).unregister(3L);
        verify(jobManager).replace(any(JobDefinition.class));
    }

    @Test
    void deleteFailureRestoresPreviousSchedule() {
        SysJob job = job(4L, 1, "0 0 * * * *");
        doReturn(job).when(service).getById(4L);
        doReturn(false).when(service).removeById(4L);

        assertThatThrownBy(() -> service.deleteJob(4L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("删除任务失败");

        verify(jobManager).unregister(4L);
        verify(jobManager).replace(any(JobDefinition.class));
    }

    private SysJob job(Long id, int status, String cron) {
        SysJob job = new SysJob();
        job.setId(id);
        job.setName("job-" + id);
        job.setExecutor("demo");
        job.setCron(cron);
        job.setStatus(status);
        job.setConcurrentGuard(1);
        return job;
    }

    private JobSaveReq request(String cron) {
        JobSaveReq req = new JobSaveReq();
        req.setName("updated");
        req.setExecutor("demo");
        req.setCron(cron);
        req.setConcurrentGuard(1);
        return req;
    }
}
