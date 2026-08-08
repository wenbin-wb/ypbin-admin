/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * 定时任务新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-07
 */
@Data
public class JobSaveReq {

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /** 执行器名称 */
    @NotBlank(message = "执行器名称不能为空")
    private String executor;

    /** Spring Cron 表达式 */
    @NotBlank(message = "Cron 表达式不能为空")
    private String cron;

    /** 固定间隔秒数 */
    @PositiveOrZero(message = "固定间隔秒数不能小于 0")
    private Long fixedRateSeconds;

    /** 执行参数 */
    private String args;

    /** 执行超时秒数 */
    @PositiveOrZero(message = "执行超时秒数不能小于 0")
    private Long timeoutSeconds;

    /** 是否启用集群防重 */
    @NotNull(message = "并发控制不能为空")
    private Integer concurrentGuard;
}
