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
import cn.ypbin.admin.system.mapper.SysMessageMapper;
import cn.ypbin.admin.system.model.query.MessageQuery;
import cn.ypbin.admin.system.service.SysMessageService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户消息服务实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
public class SysMessageServiceImpl extends BaseServiceImpl<SysMessageMapper, SysMessage>
    implements SysMessageService {

    @Override
    public PageResult<SysMessage> pageMessages(Long userId, MessageQuery query) {
        return page(query, new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getReceiverUserId, userId)
            .eq(query.getReadStatus() != null, SysMessage::getReadStatus, query.getReadStatus())
            .eq(query.getMessageType() != null, SysMessage::getMessageType, query.getMessageType())
            .orderByDesc(SysMessage::getCreateTime));
    }

    @Override
    public long unreadCount(Long userId) {
        return count(new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getReceiverUserId, userId)
            .eq(SysMessage::getReadStatus, 0));
    }

    @Override
    public List<SysMessage> recent(Long userId, long limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException("最近消息数量必须在 1 到 100 之间");
        }
        return list(new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getReceiverUserId, userId)
            .orderByDesc(SysMessage::getCreateTime)
            .last("limit " + limit));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        int affected = baseMapper.update(null, new LambdaUpdateWrapper<SysMessage>()
            .eq(SysMessage::getId, id)
            .eq(SysMessage::getReceiverUserId, userId)
            .set(SysMessage::getReadStatus, 1));
        if (affected == 0) {
            throw new BusinessException("消息不存在或无权操作");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        int affected = baseMapper.delete(new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getId, id)
            .eq(SysMessage::getReceiverUserId, userId));
        if (affected == 0) {
            throw new BusinessException("消息不存在或无权操作");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        baseMapper.update(null, new LambdaUpdateWrapper<SysMessage>()
            .eq(SysMessage::getReceiverUserId, userId)
            .eq(SysMessage::getReadStatus, 0)
            .set(SysMessage::getReadStatus, 1));
    }
}
