/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.job;

import cn.ypbin.admin.system.service.TrackAggregateService;
import cn.ypbin.admin.system.service.support.TrackAggregateWindows;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 埋点分析聚合任务（XXL-JOB 执行器）。
 *
 * <p>由 xxl-job-admin 按 cron 周期触发（建议每小时一次），重算最近
 * {@link TrackAggregateWindows#DEFAULT_WINDOW_DAYS} 天的三张聚合表。注册执行器名
 * {@code trackAggregateScan}。</p>
 *
 * <p><strong>为什么是「最近 2 天」而不是「只算昨天」</strong>：事件会延迟到达，
 * 重算窗口必须覆盖延迟；窗口重算天然自愈——上次失败的那天会在下次跑窗口时被补上，
 * 不需要额外的水位表。</p>
 *
 * <h3>任务参数（手工回填，见方案第八节）</h3>
 * <ul>
 *   <li>不传参数：默认最近 {@link TrackAggregateWindows#DEFAULT_WINDOW_DAYS} 天，<strong>与既有行为完全一致</strong>；</li>
 *   <li>{@code statDate=2026-09-01}：只重算这一天，用于补阶段 1/2 的历史明细、或补任务中断留下的空洞；</li>
 *   <li>{@code days=30}：重算最近 30 天（含今天）。</li>
 * </ul>
 * <p>参数非法即<strong>显式失败</strong>（不静默回落默认窗口）——详见
 * {@link TrackAggregateWindows#resolveDates(String, LocalDate)}。</p>
 *
 * <p><strong>为什么每天单独一个事务</strong>：某一天失败不应影响其它天。
 * 因此本任务<strong>刻意在日循环内调用聚合服务</strong>（每次都开独立事务）：
 * 循环内 DB 调用是有意为之，不是 N+1 —— 单天粒度是幂等重算与失败隔离的最小单位
 * （口径见 {@code TrackAggregateServiceImpl#rebuildDate} 的类注释）。
 * 单天失败在这里记完整堆栈后继续（与 {@link NoticePublishXxlJob} 同策略），
 * 错误被看见而非被吞掉；要判定整体失败请以日志为准，不在任务里静默汇总。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Component
@RequiredArgsConstructor
public class TrackAggregateXxlJob {

    private static final Logger log = LoggerFactory.getLogger(TrackAggregateXxlJob.class);

    private final TrackAggregateService trackAggregateService;

    @XxlJob("trackAggregateScan")
    public void execute() {
        String jobParam = XxlJobHelper.getJobParam();
        List<LocalDate> dates;
        try {
            dates = TrackAggregateWindows.resolveDates(jobParam, LocalDate.now());
        } catch (IllegalArgumentException e) {
            // 禁静默兜底：参数写错就必须让这次调度失败，否则「以为补了 30 天、实际只补了 2 天」无人察觉
            log.error("埋点聚合任务参数非法，本次调度中止，param={}", jobParam, e);
            throw e;
        }
        for (LocalDate date : dates) {
            try {
                trackAggregateService.rebuildDate(date);
            } catch (Exception e) {
                log.error("埋点聚合失败，statDate={}", date, e);
            }
        }
        log.info("埋点聚合窗口重算完成，param={}，窗口={}", jobParam, dates);
    }
}
