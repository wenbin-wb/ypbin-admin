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

import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.model.query.NoticeQuery;
import cn.ypbin.admin.system.model.req.NoticeSaveReq;
import cn.ypbin.admin.system.model.resp.NoticeResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;

/**
 * 公告服务。
 *
 * @author wenbin
 * @since 2026-08-09
 */
public interface SysNoticeService extends BaseService<SysNotice> {

    PageResult<NoticeResp> pageNotices(NoticeQuery query);

    /**
     * 公告详情（含正文）。
     *
     * <p>列表查询刻意排除 {@code content}（富文本体积大）；编辑/预览前必须在这里补齐，
     * 否则用列表行提交会把正文清空。</p>
     *
     * @param id 公告 ID
     * @return 公告详情
     */
    NoticeResp getNotice(Long id);

    void createNotice(NoticeSaveReq req);

    void updateNotice(Long id, NoticeSaveReq req);

    void publish(Long id);

    void publishScheduled(Long id, long expectedVersion);

    void revoke(Long id);

    void deleteNotice(Long id);
}
