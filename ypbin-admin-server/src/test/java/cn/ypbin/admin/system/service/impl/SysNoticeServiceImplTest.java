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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.mapper.SysNoticeMapper;
import cn.ypbin.admin.system.service.NoticePublishService;
import cn.ypbin.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link SysNoticeServiceImpl} 发布状态机测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class SysNoticeServiceImplTest {

    private SysNoticeMapper noticeMapper;
    private NoticePublishService publishService;
    private SysNoticeServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(SysNoticeMapper.class);
        publishService = mock(NoticePublishService.class);
        service = new SysNoticeServiceImpl(publishService);
        ReflectionTestUtils.setField(service, "baseMapper", noticeMapper);
    }

    @Test
    void duplicatePublishIsRejected() {
        SysNotice notice = notice(2, 3L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);

        assertThatThrownBy(() -> service.publish(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("公告已发布，禁止重复发布");

        verify(noticeMapper, never()).publishCas(1L, 2, 3L, 4L, null);
        verify(publishService, never()).freezeDeliveries(notice, 4L);
    }

    @Test
    void casFailureDoesNotFreezeDeliveries() {
        SysNotice notice = notice(0, 3L);
        when(noticeMapper.selectById(1L)).thenReturn(notice);
        when(noticeMapper.publishCas(eq(1L), eq(0), eq(3L), eq(4L), any()))
            .thenReturn(0);

        assertThatThrownBy(() -> service.publish(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("公告状态已变化，发布失败");

        verify(publishService, never()).freezeDeliveries(notice, 4L);
    }

    private SysNotice notice(int publishStatus, long publishVersion) {
        SysNotice notice = new SysNotice();
        notice.setId(1L);
        notice.setTenantId(10L);
        notice.setTitle("公告");
        notice.setContent("内容");
        notice.setNoticeScope(1);
        notice.setNotifyMethods("site");
        notice.setPublishType(1);
        notice.setPublishStatus(publishStatus);
        notice.setPublishVersion(publishVersion);
        return notice;
    }
}
