/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.provider;

import cn.ypbin.admin.modules.system.entity.SysJobLog;
import cn.ypbin.admin.modules.system.mapper.SysJobLogMapper;
import cn.ypbin.starter.job.core.JobContext;
import cn.ypbin.starter.job.core.JobExecutionListener;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 任务执行监听器：把调度器的回调记录写入 {@code sys_job_log}。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
@RequiredArgsConstructor
public class DbJobExecutionListener implements JobExecutionListener {

    private final SysJobLogMapper jobLogMapper;

    @Override
    public void onSkip(JobContext context) {
        save(context, 0, 0L, null);
    }

    @Override
    public void onSuccess(JobContext context, long durationMs) {
        save(context, 1, durationMs, null);
    }

    @Override
    public void onError(JobContext context, long durationMs, Throwable error) {
        save(context, 2, durationMs, error != null ? error.getMessage() : "未知错误");
    }

    private void save(JobContext context, int outcome, long durationMs, String errorMsg) {
        SysJobLog log = new SysJobLog();
        log.setJobId(context.getJobId());
        log.setJobName(context.getJobName());
        log.setExecutor(context.getExecutor());
        log.setTriggerTime(context.getTriggerTime());
        log.setManual(context.isManual() ? 1 : 0);
        log.setOutcome(outcome);
        log.setDurationMs(durationMs);
        log.setErrorMsg(errorMsg);
        jobLogMapper.insert(log);
    }
}
