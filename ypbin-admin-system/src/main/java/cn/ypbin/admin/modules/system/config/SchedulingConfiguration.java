/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 本地调度（单体部署的埋点聚合任务使用）。
 *
 * <p>微服务版由 XXL-JOB 触发，单体版没有调度中心，改用 Spring 的
 * {@code @Scheduled}；这里只打开调度开关，具体任务见 {@code TrackAggregateScheduler}。</p>
 *
 * @author wenbin
 * @since 2026-11-11
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
