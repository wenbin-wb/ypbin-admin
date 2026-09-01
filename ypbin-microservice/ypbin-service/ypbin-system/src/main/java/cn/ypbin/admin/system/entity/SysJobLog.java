/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 定时任务执行日志。全局表，不隔离租户。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_job_log")
public class SysJobLog extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private Long jobId;

    /** 任务名称 */
    private String jobName;

    /** 执行器名称 */
    private String executor;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 是否手动触发 */
    private Integer manual;

    /** 执行结果：0 跳过、1 成功、2 失败 */
    private Integer outcome;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 错误信息 */
    private String errorMsg;
}
