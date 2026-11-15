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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.mapper.SysNoticeMapper;
import cn.ypbin.admin.system.model.req.NoticeSaveReq;
import cn.ypbin.admin.system.service.NoticePublishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link SysNoticeServiceImpl} 公告投放客户端归一化测试。
 *
 * @author wenbin
 * @since 2026-11-15
 */
class SysNoticeServiceImplTest {

    private SysNoticeMapper noticeMapper;
    private SysNoticeServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(SysNoticeMapper.class);
        NoticePublishService publishService = mock(NoticePublishService.class);
        service = new SysNoticeServiceImpl(publishService);
        ReflectionTestUtils.setField(service, "baseMapper", noticeMapper);
    }

    @Test
    void createDraftNormalizesClientIds() {
        NoticeSaveReq req = draft();
        req.setClientIds(" miniapp , web-admin ,, miniapp ");
        when(noticeMapper.insert(any(SysNotice.class))).thenReturn(1);

        service.createNotice(req);

        ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
        verify(noticeMapper).insert(captor.capture());
        assertThat(captor.getValue().getClientIds()).isEqualTo("miniapp,web-admin");
    }

    @Test
    void blankClientIdsBecomesEmptyString() {
        NoticeSaveReq req = draft();
        req.setClientIds("   ");
        when(noticeMapper.insert(any(SysNotice.class))).thenReturn(1);

        service.createNotice(req);

        ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
        verify(noticeMapper).insert(captor.capture());
        // 空串非 null：edit 时才能在 update-strategy=not_null 下真正清空投放客户端
        assertThat(captor.getValue().getClientIds()).isEmpty();
    }

    private NoticeSaveReq draft() {
        NoticeSaveReq req = new NoticeSaveReq();
        req.setTitle("公告");
        req.setContent("内容");
        req.setPublishStatus(0);
        return req;
    }
}
