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
import java.util.List;
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

    /**
     * 查询当前有效（已发布且在生效期内）的公告，置顶优先、发布时间倒序。
     *
     * <p>供端侧（小程序）经内部端点读取：只回「已发布 + 未过期」的公告，草稿/待发布/已撤回一律不可见。
     * 端侧服务禁止直连 system 的表，故查询与筛选口径都收口在本方法一处。</p>
     *
     * @return 有效公告列表
     */
    List<NoticeResp> listPublishedNotices();

    /**
     * 按 ID 查询单条有效公告（已发布且在生效期内）。
     *
     * @param id 公告 ID
     * @return 有效公告，不可见时返回 null
     */
    NoticeResp getPublishedNotice(Long id);
}
