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

import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.mapper.SysNoticeMapper;
import cn.ypbin.admin.system.service.NoticePublishService;
import cn.ypbin.admin.system.service.SysNoticeService;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 公告定时发布与可靠投递扫描任务（XXL-JOB 执行器）。
 *
 * <p>由 xxl-job-admin 按 cron 周期触发，扫描到期待发布公告执行发布，并驱动可靠投递
 * （恢复滞留投递 + 派发到期待投递）。注册执行器名 {@code noticePublishScan}。</p>
 *
 * @author wenbin
 * @since 2026-09-05
 */
@Component
@RequiredArgsConstructor
public class NoticePublishXxlJob {

    private static final Logger log = LoggerFactory.getLogger(NoticePublishXxlJob.class);
    private static final int PENDING = 1;
    private static final int NOTICE_BATCH_SIZE = 100;

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeService noticeService;
    private final NoticePublishService noticePublishService;

    @XxlJob("noticePublishScan")
    public void execute() {
        List<SysNotice> due = TenantContext.executeIgnore(() -> noticeMapper.selectList(
            new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getPublishStatus, PENDING)
                .le(SysNotice::getScheduledTime, LocalDateTime.now())
                .orderByAsc(SysNotice::getScheduledTime)
                .last("LIMIT " + NOTICE_BATCH_SIZE)));
        int published = 0;
        for (SysNotice notice : due) {
            try {
                TenantContext.runWithTenant(notice.getTenantId(),
                    () -> noticeService.publishScheduled(notice.getId(), notice.getPublishVersion()));
                published++;
            } catch (Exception e) {
                log.error("定时公告发布失败，noticeId={}，tenantId={}",
                    notice.getId(), notice.getTenantId(), e);
            }
        }
        noticePublishService.recoverProcessing();
        noticePublishService.dispatchPending();
        log.info("公告扫描完成，待发布={}，成功发布={}", due.size(), published);
    }
}
