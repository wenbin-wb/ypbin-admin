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
import cn.ypbin.admin.system.model.req.NoticeSaveReq;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 公告服务。
 *
 * @author wenbin
 * @since 2026-08-09
 */
public interface SysNoticeService extends BaseService<SysNotice> {

    List<SysNotice> listNotices();

    void createNotice(NoticeSaveReq req);

    void updateNotice(Long id, NoticeSaveReq req);

    void publish(Long id);

    void publishScheduled(Long id, long expectedVersion);

    void revoke(Long id);

    void deleteNotice(Long id);
}
