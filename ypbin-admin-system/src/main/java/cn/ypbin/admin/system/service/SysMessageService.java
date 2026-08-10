/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysMessage;
import cn.ypbin.admin.system.model.query.MessageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 当前用户消息服务。
 *
 * @author wenbin
 * @since 2026-08-09
 */
public interface SysMessageService extends BaseService<SysMessage> {

    PageResult<SysMessage> pageMessages(Long userId, MessageQuery query);

    long unreadCount(Long userId);

    List<SysMessage> recent(Long userId, long limit);

    void markRead(Long userId, Long id);

    void delete(Long userId, Long id);

    void markAllRead(Long userId);
}
