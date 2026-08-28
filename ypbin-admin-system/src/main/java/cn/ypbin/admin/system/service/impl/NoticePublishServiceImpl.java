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

import cn.ypbin.admin.system.entity.SysMessage;
import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.entity.SysNoticeDelivery;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysMessageMapper;
import cn.ypbin.admin.system.mapper.SysNoticeDeliveryMapper;
import cn.ypbin.admin.system.mapper.SysNoticeMapper;
import cn.ypbin.admin.system.service.NoticePublishService;
import cn.ypbin.admin.system.service.support.NoticeTargetResolver;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.messaging.mail.MailService;
import cn.ypbin.starter.messaging.push.PushService;
import cn.ypbin.starter.messaging.sms.SmsService;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 公告可靠投递服务实现。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Service
@RequiredArgsConstructor
public class NoticePublishServiceImpl implements NoticePublishService {

    private static final Logger log = LoggerFactory.getLogger(NoticePublishServiceImpl.class);
    private static final int PUBLISHED = 2;
    private static final int MAX_RETRIES = 3;
    private static final int ERROR_MAX_LENGTH = 1000;
    private static final int BATCH_SIZE = 100;
    private static final Set<String> CHANNELS = Set.of("site", "email", "sms");

    /** 投递状态：待投递 */
    private static final String DELIVERY_PENDING = "PENDING";
    /** 投递状态：待重试 */
    private static final String DELIVERY_RETRY = "RETRY";
    /** 投递状态：已失败 */
    private static final String DELIVERY_FAILED = "FAILED";

    private final NoticeTargetResolver targetResolver;
    private final SysNoticeMapper noticeMapper;
    private final SysNoticeDeliveryMapper deliveryMapper;
    private final SysMessageMapper messageMapper;
    private final MailService mailService;
    private final PushService pushService;
    private final ObjectProvider<SmsService> smsServiceProvider;

    @Override
    public void validateDeliveries(SysNotice notice) {
        Set<String> channels = parseChannels(notice.getNotifyMethods());
        validateChannelConfiguration(channels);
        if (targetResolver.resolveTargets(notice).isEmpty()) {
            throw new BusinessException("公告没有可投递的目标用户");
        }
    }

    @Override
    public void freezeDeliveries(SysNotice notice, long publishVersion) {
        Set<String> channels = parseChannels(notice.getNotifyMethods());
        validateChannelConfiguration(channels);
        List<SysUser> targets = targetResolver.resolveTargets(notice);
        if (targets.isEmpty()) {
            throw new BusinessException("公告没有可投递的目标用户");
        }
        LocalDateTime now = LocalDateTime.now();
        for (SysUser user : targets) {
            for (String channel : channels) {
                SysNoticeDelivery delivery = new SysNoticeDelivery();
                delivery.setTenantId(notice.getTenantId());
                delivery.setNoticeId(notice.getId());
                delivery.setPublishVersion(publishVersion);
                delivery.setReceiverUserId(user.getId());
                delivery.setChannel(channel);
                delivery.setTargetAddress(targetAddress(channel, user));
                delivery.setDeliveryStatus(DELIVERY_PENDING);
                delivery.setRetryCount(0);
                delivery.setNextRetryTime(now);
                delivery.setCreateTime(now);
                delivery.setUpdateTime(now);
                deliveryMapper.insert(delivery);
            }
        }
    }

    @Override
    public void recoverProcessing() {
        LocalDateTime now = LocalDateTime.now();
        TenantContext.runIgnore(() -> deliveryMapper.recoverStale(now.minusMinutes(10), now, MAX_RETRIES));
    }

    @Override
    public void dispatchPending() {
        LocalDateTime now = LocalDateTime.now();
        List<SysNoticeDelivery> due = TenantContext.executeIgnore(() -> deliveryMapper.selectList(
            new LambdaQueryWrapper<SysNoticeDelivery>()
                .in(SysNoticeDelivery::getDeliveryStatus, DELIVERY_PENDING, DELIVERY_RETRY)
                .le(SysNoticeDelivery::getNextRetryTime, now)
                .orderByAsc(SysNoticeDelivery::getNextRetryTime)
                .last("LIMIT " + BATCH_SIZE)));
        for (SysNoticeDelivery delivery : due) {
            try {
                TenantContext.runWithTenant(delivery.getTenantId(), () -> dispatchNotice(delivery.getId()));
            } catch (Exception e) {
                log.error("公告投递记录处理异常，deliveryId={}", delivery.getId(), e);
            }
        }
    }

    @Override
    public void dispatchNotice(Long deliveryId) {
        SysNoticeDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException("公告投递记录不存在");
        }
        String expectedStatus = delivery.getDeliveryStatus();
        LocalDateTime now = LocalDateTime.now();
        if (deliveryMapper.claim(deliveryId, expectedStatus, now) != 1) {
            return;
        }
        try {
            SysNotice notice = noticeMapper.selectById(delivery.getNoticeId());
            if (!isCurrentPublishedVersion(notice, delivery)) {
                requireAffected(deliveryMapper.markCancelled(deliveryId,
                    "公告已撤回或发布版本已变化", LocalDateTime.now()), "取消投递失败");
                return;
            }
            deliver(delivery, notice);
            requireAffected(deliveryMapper.markSuccess(deliveryId, LocalDateTime.now()), "更新投递成功状态失败");
        } catch (Exception e) {
            persistFailure(delivery, e);
        }
    }

    private void deliver(SysNoticeDelivery delivery, SysNotice notice) {
        switch (delivery.getChannel()) {
            case "site" -> deliverSite(delivery, notice);
            case "email" -> deliverEmail(delivery, notice);
            case "sms" -> deliverSms(delivery, notice);
            default -> throw new IllegalStateException("未知投递通道：" + delivery.getChannel());
        }
    }

    private void deliverSite(SysNoticeDelivery delivery, SysNotice notice) {
        SysMessage message = new SysMessage();
        message.setId(IdWorker.getId());
        message.setTenantId(delivery.getTenantId());
        message.setNoticeId(delivery.getNoticeId());
        message.setPublishVersion(delivery.getPublishVersion());
        message.setReceiverUserId(delivery.getReceiverUserId());
        message.setTitle(notice.getTitle());
        message.setContent(notice.getContent());
        message.setMessageType(1);
        message.setReadStatus(0);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(message.getCreateTime());
        message.setStatus(1);
        message.setIsDeleted(0);
        try {
            messageMapper.insertNoticeMessage(message);
        } catch (DuplicateKeyException e) {
            if (!noticeMessageExists(delivery)) {
                throw e;
            }
            log.debug("站内信已存在，按幂等成功处理，deliveryId={}", delivery.getId());
        }
        try {
            pushService.sendToUser(String.valueOf(delivery.getReceiverUserId()), "message-unread", 1);
        } catch (Exception e) {
            log.error("站内信刷新提示发送失败，deliveryId={}，userId={}",
                delivery.getId(), delivery.getReceiverUserId(), e);
        }
    }

    private boolean noticeMessageExists(SysNoticeDelivery delivery) {
        return messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getTenantId, delivery.getTenantId())
            .eq(SysMessage::getNoticeId, delivery.getNoticeId())
            .eq(SysMessage::getPublishVersion, delivery.getPublishVersion())
            .eq(SysMessage::getReceiverUserId, delivery.getReceiverUserId())) > 0;
    }

    private void deliverEmail(SysNoticeDelivery delivery, SysNotice notice) {
        if (delivery.getTargetAddress() == null || delivery.getTargetAddress().isBlank()) {
            throw new IllegalStateException("接收人未配置邮箱");
        }
        mailService.sendHtml(delivery.getTargetAddress(), notice.getTitle(), notice.getContent());
    }

    private void deliverSms(SysNoticeDelivery delivery, SysNotice notice) {
        if (delivery.getTargetAddress() == null || delivery.getTargetAddress().isBlank()) {
            throw new IllegalStateException("接收人未配置手机号");
        }
        SmsService smsService = smsServiceProvider.getIfAvailable();
        if (smsService == null || !smsService.isConfigured()) {
            throw new IllegalStateException("短信服务未配置");
        }
        if (!smsService.send(delivery.getTargetAddress(), notice.getTitle())) {
            throw new IllegalStateException("短信发送返回失败");
        }
    }

    private void persistFailure(SysNoticeDelivery delivery, Exception error) {
        int retryCount = delivery.getRetryCount() + 1;
        boolean exhausted = retryCount >= MAX_RETRIES;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryTime = exhausted ? null : now.plusMinutes(retryCount);
        int affected = deliveryMapper.markFailure(delivery.getId(), exhausted ? DELIVERY_FAILED : DELIVERY_RETRY,
            retryCount, nextRetryTime, errorMessage(error), now);
        if (affected != 1) {
            log.error("公告投递失败状态持久化失败，deliveryId={}", delivery.getId(), error);
            throw new IllegalStateException("公告投递失败状态持久化失败", error);
        }
        log.error("公告投递失败，deliveryId={}，retryCount={}", delivery.getId(), retryCount, error);
    }

    private Set<String> parseChannels(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new BusinessException("通知方式不能为空");
        }
        Set<String> channels = new LinkedHashSet<>();
        for (String value : csv.split(",", -1)) {
            String channel = value.trim();
            if (channel.isEmpty()) {
                throw new BusinessException("通知方式包含空值");
            }
            if (!CHANNELS.contains(channel)) {
                throw new BusinessException("未知通知方式：" + channel);
            }
            channels.add(channel);
        }
        if (channels.isEmpty()) {
            throw new BusinessException("通知方式不能为空");
        }
        return channels;
    }

    private void validateChannelConfiguration(Set<String> channels) {
        if (channels.contains("email") && !mailService.isConfigured()) {
            throw new BusinessException("邮件服务未配置，无法发布邮件通知");
        }
        if (channels.contains("sms")) {
            SmsService smsService = smsServiceProvider.getIfAvailable();
            if (smsService == null || !smsService.isConfigured()) {
                throw new BusinessException("短信服务未配置，无法发布短信通知");
            }
        }
    }

    private String targetAddress(String channel, SysUser user) {
        return switch (channel) {
            case "site" -> null;
            case "email" -> user.getEmail();
            case "sms" -> user.getPhone();
            default -> throw new BusinessException("未知通知方式：" + channel);
        };
    }

    private boolean isCurrentPublishedVersion(SysNotice notice, SysNoticeDelivery delivery) {
        return notice != null && notice.getPublishStatus() == PUBLISHED
            && delivery.getPublishVersion().equals(notice.getPublishVersion());
    }

    private String errorMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return message.length() <= ERROR_MAX_LENGTH ? message : message.substring(0, ERROR_MAX_LENGTH);
    }

    private void requireAffected(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }
}
