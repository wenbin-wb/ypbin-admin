/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.controller;

import cn.ypbin.admin.modules.system.entity.SysMessage;
import cn.ypbin.admin.modules.system.model.query.MessageQuery;
import cn.ypbin.admin.modules.system.service.SysMessageService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class SysMessageController {

    private final SysMessageService messageService;

    /**
     * 分页查询当前用户站内信，可按已读状态过滤。
     */
    @GetMapping
    public R<PageResult<SysMessage>> list(@Valid MessageQuery query) {
        return R.ok(messageService.pageMessages(UserContext.getUserId(), query));
    }

    /**
     * 未读消息数。
     */
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(messageService.unreadCount(UserContext.getUserId()));
    }

    /**
     * 最近消息，包含已读和未读消息。
     */
    @GetMapping("/recent")
    public R<List<SysMessage>> recent(
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) long limit) {
        return R.ok(messageService.recent(UserContext.getUserId(), limit));
    }

    /**
     * 标记单条已读。
     */
    @Idempotent
    @Log(value = "标记站内信已读", module = "站内信")
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        messageService.markRead(UserContext.getUserId(), id);
        return R.ok();
    }

    /**
     * 删除站内信。仅能删除当前登录用户自己的消息。
     */
    @Idempotent
    @Log(value = "删除站内信", module = "站内信")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        messageService.delete(UserContext.getUserId(), id);
        return R.ok();
    }

    /**
     * 全部标记已读。
     */
    @Idempotent
    @Log(value = "站内信全部标记已读", module = "站内信")
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        messageService.markAllRead(UserContext.getUserId());
        return R.ok();
    }
}
