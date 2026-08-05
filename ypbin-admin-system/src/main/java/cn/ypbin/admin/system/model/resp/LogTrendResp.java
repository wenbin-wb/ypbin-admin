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

import lombok.Data;

/**
 * 操作日志按天趋势响应。
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Data
public class LogTrendResp {

    /**
     * 日期（yyyy-MM-dd）。
     */
    private String date;

    /**
     * 当天操作日志条数。
     */
    private Long count;
}
