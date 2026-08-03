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
import cn.ypbin.starter.job.core.JobContext;
import cn.ypbin.starter.job.core.JobHandler;
import cn.ypbin.starter.job.core.YpbinJob;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定时发布扫描任务：将已到发布时间的待发布公告置为已发布。
 *
 * <p>扫描 publish_status=1（待发布）且 scheduled_time&lt;=now 的公告，置为已发布并回填发布时间。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
@YpbinJob("noticePublishScan")
@RequiredArgsConstructor
public class NoticePublishJob implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(NoticePublishJob.class);

    /** 待发布 */
    private static final int STATUS_PENDING = 1;
    /** 已发布 */
    private static final int STATUS_PUBLISHED = 2;

    private final SysNoticeMapper noticeMapper;
    private final NoticePublishService noticePublishService;

    @Override
    public void execute(JobContext context) {
        LocalDateTime now = LocalDateTime.now();
        List<SysNotice> due = noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
            .eq(SysNotice::getPublishStatus, STATUS_PENDING)
            .le(SysNotice::getScheduledTime, now));
        if (due.isEmpty()) {
            return;
        }
        for (SysNotice notice : due) {
            SysNotice update = new SysNotice();
            update.setId(notice.getId());
            update.setPublishStatus(STATUS_PUBLISHED);
            update.setPublishTime(now);
            noticeMapper.updateById(update);
            // 状态流转为已发布后触发推送（解析目标→站内信→邮件/短信→SSE）
            notice.setPublishStatus(STATUS_PUBLISHED);
            notice.setPublishTime(now);
            noticePublishService.dispatch(notice);
        }
        log.info("[定时发布] 本次发布公告 {} 条", due.size());
    }
}
