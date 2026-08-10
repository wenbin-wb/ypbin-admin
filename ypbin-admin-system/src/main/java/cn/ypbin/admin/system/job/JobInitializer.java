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

import cn.ypbin.admin.system.service.SysJobService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final SysJobService jobService;

    @PostConstruct
    public void init() {
        jobService.reconcileRuntime();
    }

    @Scheduled(
        fixedDelayString = "${ypbin.admin.job.reconcile-delay:30000}",
        initialDelayString = "${ypbin.admin.job.reconcile-delay:30000}")
    public void reconcile() {
        jobService.reconcileRuntime();
    }
}
