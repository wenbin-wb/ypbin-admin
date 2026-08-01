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

import cn.ypbin.starter.job.core.JobContext;
import cn.ypbin.starter.job.core.JobHandler;
import cn.ypbin.starter.job.core.YpbinJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例任务：清理临时文件。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@YpbinJob("cleanTempFile")
public class CleanTempFileJob implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(CleanTempFileJob.class);

    @Override
    public void execute(JobContext context) throws Exception {
        log.info("[示例任务] 作业={}, 参数={}, 手动触发={}", context.getJobName(), context.getArgs(), context.isManual());
        // 实际清理逻辑在此
    }
}
