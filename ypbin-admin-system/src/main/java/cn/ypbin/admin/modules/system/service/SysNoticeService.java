/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import cn.ypbin.admin.modules.system.entity.SysNotice;
import cn.ypbin.admin.modules.system.model.req.NoticeSaveReq;
import cn.ypbin.admin.modules.system.model.resp.NoticeResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 公告服务。
 *
 * @author wenbin
 * @since 2026-08-09
 */
public interface SysNoticeService extends BaseService<SysNotice> {

    List<NoticeResp> listNotices();

    /**
     * 对外可见的公告列表：已发布且未过期，置顶优先、发布时间倒序。
     *
     * @return 公告列表
     */
    List<NoticeResp> listPublishedNotices();

    /**
     * 按 ID 读取对外可见的公告（已发布且未过期）。
     *
     * @param id 公告 ID
     * @return 公告；不存在或不可见时为 null
     */
    NoticeResp getPublishedNotice(Long id);

    void createNotice(NoticeSaveReq req);

    void updateNotice(Long id, NoticeSaveReq req);

    void publish(Long id);

    void publishScheduled(Long id, long expectedVersion);

    void revoke(Long id);

    void deleteNotice(Long id);
}
