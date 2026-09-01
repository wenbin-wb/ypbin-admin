/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 定时任务执行日志响应。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
public class JobLogResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long jobId;

    private String jobName;

    private String executor;

    private LocalDateTime triggerTime;

    private Integer manual;

    /** 0 跳过、1 成功、2 失败 */
    private Integer outcome;

    private Long durationMs;

    private String errorMsg;

    private LocalDateTime createTime;
}
