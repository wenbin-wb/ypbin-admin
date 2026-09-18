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

import java.time.LocalDate;

/**
 * 埋点聚合服务（写侧）。
 *
 * <p>与只读的 {@code TrackEventService} 分开：写侧跑在 XXL-JOB 的线程上、没有租户上下文，
 * 只写三张聚合表，不与查询侧共享任何状态。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public interface TrackAggregateService {

    /**
     * 重算某一天的三张聚合表（幂等：先按天/按会话删除再写入）。
     *
     * <p>整体在一个事务里：任何一步失败即整日回滚，避免出现「事件聚合已更新、会话还是旧的」这种
     * 内部不一致的中间状态。</p>
     *
     * @param statDate 统计日期
     */
    void rebuildDate(LocalDate statDate);
}
