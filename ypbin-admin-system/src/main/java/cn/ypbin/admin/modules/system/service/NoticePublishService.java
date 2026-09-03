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

/**
 * 公告可靠投递服务。
 *
 * <p>在公告发布事务中冻结接收人与通道，事务提交后由任务扫描投递记录并持久化处理结果。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
public interface NoticePublishService {

    /**
     * 校验公告的接收人与通道配置。
     *
     * @param notice 公告
     */
    void validateDeliveries(SysNotice notice);

    void freezeDeliveries(SysNotice notice, long publishVersion);

    /**
     * 恢复超时的处理中记录。
     */
    void recoverProcessing();

    /**
     * 扫描并处理待投递记录。
     */
    void dispatchPending();

    /**
     * 处理单条投递记录。
     *
     * @param deliveryId 投递记录 ID
     */
    void dispatchNotice(Long deliveryId);
}
