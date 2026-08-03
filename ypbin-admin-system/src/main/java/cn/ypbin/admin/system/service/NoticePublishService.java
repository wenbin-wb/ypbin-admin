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

/**
 * 公告发布推送服务。
 *
 * <p>发布动作的统一入口：按通知范围解析目标用户，写站内信，并按通知方式经邮件/短信/SSE 触达。
 * 供公告的「立即发布」与「定时发布扫描」共用。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
public interface NoticePublishService {

    /**
     * 执行一条已发布公告的推送（解析目标 → 写站内信 → 邮件/短信 → SSE）。
     * 幂等性由调用方保证（仅在状态流转为「已发布」时调用一次）。
     *
     * @param notice 公告
     */
    void dispatch(SysNotice notice);
}
