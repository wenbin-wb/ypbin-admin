/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.job.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 定时任务。全局表，不隔离租户。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_job")
public class SysJob extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务名称 */
    private String name;

    /** 执行器名称（对应 @YpbinJob 声明的名称） */
    private String executor;

    /** cron 表达式（与 fixedRateSeconds 二选一） */
    private String cron;

    /** 固定频率秒数（与 cron 二选一） */
    private Long fixedRateSeconds;

    /** 执行参数 */
    private String args;

    /** 执行超时秒数 */
    private Long timeoutSeconds;

    /** 是否启用集群防重 */
    private Integer concurrentGuard;
}
