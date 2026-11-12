/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.job;

import cn.ypbin.admin.modules.system.service.TrackAggregateService;
import cn.ypbin.admin.modules.system.service.support.TrackAggregateWindows;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 埋点聚合的本地调度（单体部署没有 XXL-JOB 时的默认触发方式）。
 *
 * <p>窗口与失败策略与 {@link TrackAggregateXxlJob} 保持一致：按小时重算最近
 * {@link TrackAggregateWindows#DEFAULT_WINDOW_DAYS} 天，单天失败只记录堆栈并继续，
 * 下次窗口会自愈补算。启用 XXL-JOB 的部署可把
 * {@code ypbin.tracking.aggregate-scheduled} 置为 false，避免两边同时触发（重算是幂等的，
 * 同时跑只会浪费一次数据库扫描）。</p>
 *
 * @author wenbin
 * @since 2026-11-11
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ypbin.tracking", name = "aggregate-scheduled",
    havingValue = "true", matchIfMissing = true)
public class TrackAggregateScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrackAggregateScheduler.class);

    private final TrackAggregateService trackAggregateService;

    /**
     * 每 5 分钟重算一次聚合窗口。
     *
     * <p>单体测试环境事件量小，5 分钟足够新鲜；窗口只有 2 天，单次重算成本很低。
     * 需要降频时改这里即可（微服务版仍是 XXL-JOB 按小时触发）。</p>
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void aggregate() {
        LocalDate today = LocalDate.now();
        for (int offset = 0; offset < TrackAggregateWindows.DEFAULT_WINDOW_DAYS; offset++) {
            LocalDate statDate = today.minusDays(offset);
            try {
                trackAggregateService.rebuildDate(statDate);
            } catch (Exception e) {
                log.error("埋点聚合失败，statDate={}", statDate, e);
            }
        }
    }
}
