/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.controller;

import cn.ypbin.admin.system.entity.SysMessage;
import cn.ypbin.admin.system.mapper.SysMessageMapper;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.security.core.LoginHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户站内信接口。操作对象恒为当前登录用户，仅需登录、不挂管理权限。
 *
 * @author wenbin
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/user/messages")
@RequiredArgsConstructor
public class SysMessageController extends BaseController {

    private final SysMessageMapper messageMapper;

    /**
     * 分页查询当前用户站内信，可按已读状态过滤。
     */
    @GetMapping
    public R<Page<SysMessage>> list(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long pageSize,
                                    @RequestParam(required = false) Integer readStatus,
                                    @RequestParam(required = false) Integer messageType) {
        Long userId = LoginHelper.getUserId();
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getReceiverUserId, userId)
            .eq(readStatus != null, SysMessage::getReadStatus, readStatus)
            .eq(messageType != null, SysMessage::getMessageType, messageType)
            .orderByDesc(SysMessage::getCreateTime);
        return ok(messageMapper.selectPage(new Page<>(page, pageSize), wrapper));
    }

    /**
     * 未读消息数。
     */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        Long userId = LoginHelper.getUserId();
        long count = messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getReceiverUserId, userId)
            .eq(SysMessage::getReadStatus, 0));
        return ok(count);
    }

    /**
     * 最近未读消息（铃铛下拉展示用）。
     */
    @GetMapping("/recent")
    public R<List<SysMessage>> recent(@RequestParam(defaultValue = "10") long limit) {
        Long userId = LoginHelper.getUserId();
        return ok(messageMapper.selectList(new LambdaQueryWrapper<SysMessage>()
            .eq(SysMessage::getReceiverUserId, userId)
            .orderByDesc(SysMessage::getCreateTime)
            .last("limit " + limit)));
    }

    /**
     * 标记单条已读。
     */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        Long userId = LoginHelper.getUserId();
        messageMapper.update(null, new LambdaUpdateWrapper<SysMessage>()
            .eq(SysMessage::getId, id)
            .eq(SysMessage::getReceiverUserId, userId)
            .set(SysMessage::getReadStatus, 1));
        return ok();
    }

    /**
     * 全部标记已读。
     */
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        Long userId = LoginHelper.getUserId();
        messageMapper.update(null, new LambdaUpdateWrapper<SysMessage>()
            .eq(SysMessage::getReceiverUserId, userId)
            .eq(SysMessage::getReadStatus, 0)
            .set(SysMessage::getReadStatus, 1));
        return ok();
    }
}
