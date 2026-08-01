/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.job;

import cn.ypbin.admin.system.entity.SysJob;
import cn.ypbin.admin.system.mapper.SysJobMapper;
import cn.ypbin.starter.job.core.JobDefinition;
import cn.ypbin.starter.job.core.JobManager;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 启动时将数据库中的启用任务注册到调度器。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
@RequiredArgsConstructor
public class JobInitializer {

    private final SysJobMapper jobMapper;
    private final JobManager jobManager;

    @PostConstruct
    public void init() {
        List<SysJob> jobs = jobMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getStatus, 1));
        for (SysJob job : jobs) {
            JobDefinition def = new JobDefinition(job.getId(), job.getName(), job.getExecutor(), job.getCron());
            def.setFixedRateSeconds(job.getFixedRateSeconds());
            def.setArgs(job.getArgs());
            def.setTimeoutSeconds(job.getTimeoutSeconds() != null ? job.getTimeoutSeconds() : 0);
            def.setConcurrentGuard(job.getConcurrentGuard() == null || job.getConcurrentGuard() == 1);
            jobManager.register(def);
        }
    }
}
