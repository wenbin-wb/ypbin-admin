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

import java.time.ZonedDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
/**
 * Cron 表达式预览响应。
 *
 * @author wenbin
 * @since 2026-08-07
 */
@Getter
@Setter
@AllArgsConstructor
public class CronPreviewResp {

    /** 是否合法 */
    private boolean valid;

    /** 校验信息 */
    private String message;

    /** 计算时区 */
    private String zoneId;

    /** 后续执行时间 */
    private List<ZonedDateTime> nextExecutionTimes;
}
