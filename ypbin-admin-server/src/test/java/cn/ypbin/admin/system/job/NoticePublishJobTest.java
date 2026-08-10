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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.mapper.SysNoticeMapper;
import cn.ypbin.admin.system.service.NoticePublishService;
import cn.ypbin.admin.system.service.SysNoticeService;
import cn.ypbin.starter.job.core.JobContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link NoticePublishJob} 租户上下文测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class NoticePublishJobTest {

    @Test
    void scheduledPublishBindsExplicitTenant() {
        SysNoticeMapper mapper = mock(SysNoticeMapper.class);
        SysNoticeService noticeService = mock(SysNoticeService.class);
        NoticePublishService publishService = mock(NoticePublishService.class);
        SysNotice notice = new SysNotice();
        notice.setId(1L);
        notice.setTenantId(10L);
        notice.setPublishStatus(1);
        notice.setPublishVersion(2L);
        notice.setScheduledTime(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectList(any())).thenReturn(List.of(notice));
        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).contains(10L);
            return null;
        }).when(noticeService).publishScheduled(1L, 2L);

        new NoticePublishJob(mapper, noticeService, publishService).execute(mock(JobContext.class));

        verify(noticeService).publishScheduled(1L, 2L);
        verify(publishService).recoverProcessing();
        verify(publishService).dispatchPending();
    }
}
