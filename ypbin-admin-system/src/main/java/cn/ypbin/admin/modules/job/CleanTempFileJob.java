/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.job;

import cn.ypbin.starter.job.core.JobContext;
import cn.ypbin.starter.job.core.JobHandler;
import cn.ypbin.starter.job.core.YpbinJob;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定时任务：清理系统临时目录中过期的 ypbin 临时文件。
 *
 * <p>仅删除 {@code java.io.tmpdir} 下文件名以 {@code ypbin-} 开头且最后修改时间
 * 超过 {@link #MAX_AGE} 的普通文件，不递归删除目录、不触碰非 ypbin 前缀文件，
 * 避免误删其它进程的临时数据。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@YpbinJob("cleanTempFile")
public class CleanTempFileJob implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(CleanTempFileJob.class);

    /** 临时文件最大保留时长 */
    private static final Duration MAX_AGE = Duration.ofDays(7);

    /** 仅清理本系统创建的临时文件前缀 */
    private static final String PREFIX = "ypbin-";

    @Override
    public void execute(JobContext context) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        Path dir = Path.of(tmpDir);
        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, PREFIX + "*")) {
            Instant now = Instant.now();
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                try {
                    FileTime lastModified = Files.getLastModifiedTime(entry);
                    if (Duration.between(lastModified.toInstant(), now).compareTo(MAX_AGE) > 0) {
                        Files.deleteIfExists(entry);
                        deleted++;
                        log.debug("[ypbin-admin] 已清理过期临时文件: {}", entry.getFileName());
                    }
                } catch (IOException e) {
                    // 单文件清理失败不阻断整体，但必须记录日志暴露问题
                    log.warn("[ypbin-admin] 清理临时文件失败: {} err={}", entry.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[ypbin-admin] 扫描临时目录失败: {} err={}", dir, e.getMessage());
            return;
        }
        log.info("[ypbin-admin] 临时文件清理完成: 作业={}, 参数={}, 删除={} 个", context.getJobName(), context.getArgs(), deleted);
    }
}
