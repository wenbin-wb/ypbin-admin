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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.entity.SysNoticeDelivery;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysMessageMapper;
import cn.ypbin.admin.system.mapper.SysNoticeDeliveryMapper;
import cn.ypbin.admin.system.mapper.SysNoticeMapper;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.service.support.NoticeTargetResolver;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.messaging.mail.MailService;
import cn.ypbin.starter.messaging.push.PushService;
import cn.ypbin.starter.messaging.sms.SmsService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

/**
 * {@link NoticePublishServiceImpl} 可靠投递测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class NoticePublishServiceImplTest {

    private NoticeTargetResolver targetResolver;
    private SysNoticeMapper noticeMapper;
    private SysNoticeDeliveryMapper deliveryMapper;
    private SysMessageMapper messageMapper;
    private MailService mailService;
    private PushService pushService;
    private ObjectProvider<SmsService> smsProvider;
    private SmsService smsService;
    private NoticePublishServiceImpl service;

    @BeforeEach
    void setUp() {
        targetResolver = mock(NoticeTargetResolver.class);
        noticeMapper = mock(SysNoticeMapper.class);
        deliveryMapper = mock(SysNoticeDeliveryMapper.class);
        messageMapper = mock(SysMessageMapper.class);
        mailService = mock(MailService.class);
        pushService = mock(PushService.class);
        smsProvider = mock(ObjectProvider.class);
        smsService = mock(SmsService.class);
        service = new NoticePublishServiceImpl(targetResolver, noticeMapper,
            deliveryMapper, messageMapper, mailService, pushService, smsProvider);
    }

    @Test
    void unknownChannelRejectsPublish() {
        SysNotice notice = notice("webhook");

        assertThatThrownBy(() -> service.freezeDeliveries(notice, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("未知通知方式：webhook");

        verify(deliveryMapper, never()).insert(any(SysNoticeDelivery.class));
    }

    @Test
    void unconfiguredEmailRejectsPublish() {
        SysNotice notice = notice("email");
        when(mailService.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.freezeDeliveries(notice, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("邮件服务未配置，无法发布邮件通知");
    }

    @Test
    void claimFailureDoesNotSend() {
        SysNoticeDelivery delivery = delivery("sms", "PENDING", 0);
        when(deliveryMapper.selectById(1L)).thenReturn(delivery);
        when(deliveryMapper.claim(any(), anyString(), any())).thenReturn(0);

        service.dispatchNotice(1L);

        verify(smsProvider, never()).getIfAvailable();
        verify(messageMapper, never()).insertNoticeMessage(any());
    }

    @Test
    void smsFalsePersistsRetry() {
        SysNoticeDelivery delivery = delivery("sms", "PENDING", 0);
        SysNotice notice = publishedNotice();
        when(deliveryMapper.selectById(1L)).thenReturn(delivery);
        when(deliveryMapper.claim(any(), anyString(), any())).thenReturn(1);
        when(noticeMapper.selectById(9L)).thenReturn(notice);
        when(smsProvider.getIfAvailable()).thenReturn(smsService);
        when(smsService.isConfigured()).thenReturn(true);
        when(smsService.send("13800138000", "公告")).thenReturn(false);
        when(deliveryMapper.markFailure(any(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(1);

        service.dispatchNotice(1L);

        verify(deliveryMapper).markFailure(any(), eq("RETRY"), eq(1), any(),
            eq("短信发送返回失败"), any());
        verify(deliveryMapper, never()).markSuccess(any(), any());
    }

    @Test
    void siteDuplicateInsertStillSucceeds() {
        SysNoticeDelivery delivery = delivery("site", "PENDING", 0);
        SysNotice notice = publishedNotice();
        when(deliveryMapper.selectById(1L)).thenReturn(delivery);
        when(deliveryMapper.claim(any(), anyString(), any())).thenReturn(1);
        when(noticeMapper.selectById(9L)).thenReturn(notice);
        when(messageMapper.insertNoticeMessage(any()))
            .thenThrow(new DuplicateKeyException("duplicate"));
        when(messageMapper.selectCount(any())).thenReturn(1L);
        when(deliveryMapper.markSuccess(any(), any())).thenReturn(1);

        service.dispatchNotice(1L);

        verify(messageMapper).insertNoticeMessage(any());
        verify(deliveryMapper).markSuccess(any(), any());
    }

    @Test
    void oneFailureDoesNotPreventAnotherRecord() {
        SysNoticeDelivery failed = delivery("sms", "PENDING", 0);
        SysNoticeDelivery success = delivery("site", "PENDING", 0);
        success.setId(2L);
        when(deliveryMapper.selectList(any())).thenReturn(List.of(failed, success));
        when(deliveryMapper.selectById(1L)).thenReturn(failed);
        when(deliveryMapper.selectById(2L)).thenReturn(success);
        when(deliveryMapper.claim(any(), anyString(), any())).thenReturn(1);
        when(noticeMapper.selectById(9L)).thenReturn(publishedNotice());
        when(smsProvider.getIfAvailable()).thenReturn(smsService);
        when(smsService.isConfigured()).thenReturn(true);
        when(smsService.send(anyString(), anyString())).thenReturn(false);
        when(deliveryMapper.markFailure(any(), anyString(), anyInt(), any(), anyString(), any()))
            .thenReturn(1);
        when(deliveryMapper.markSuccess(any(), any())).thenReturn(1);

        service.dispatchNotice(1L);
        service.dispatchNotice(2L);

        verify(deliveryMapper).markFailure(eq(1L), anyString(), anyInt(), any(), anyString(), any());
        verify(deliveryMapper).markSuccess(eq(2L), any());
    }

    private SysNotice notice(String channels) {
        SysNotice notice = publishedNotice();
        notice.setNotifyMethods(channels);
        return notice;
    }

    private SysNotice publishedNotice() {
        SysNotice notice = new SysNotice();
        notice.setId(9L);
        notice.setTenantId(10L);
        notice.setTitle("公告");
        notice.setContent("内容");
        notice.setNoticeScope(1);
        notice.setNotifyMethods("site");
        notice.setPublishStatus(2);
        notice.setPublishVersion(1L);
        return notice;
    }

    private SysNoticeDelivery delivery(String channel, String status, int retryCount) {
        SysNoticeDelivery delivery = new SysNoticeDelivery();
        delivery.setId(1L);
        delivery.setTenantId(10L);
        delivery.setNoticeId(9L);
        delivery.setPublishVersion(1L);
        delivery.setReceiverUserId(20L);
        delivery.setChannel(channel);
        delivery.setTargetAddress("sms".equals(channel) ? "13800138000" : null);
        delivery.setDeliveryStatus(status);
        delivery.setRetryCount(retryCount);
        delivery.setNextRetryTime(LocalDateTime.now());
        return delivery;
    }
}
