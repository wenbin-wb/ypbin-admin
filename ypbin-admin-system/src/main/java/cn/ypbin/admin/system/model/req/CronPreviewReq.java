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
import lombok.Data;

/**
 * Cron 表达式预览请求。
 *
 * @author wenbin
 * @since 2026-08-07
 */
@Data
public class CronPreviewReq {

    /** Spring Cron 表达式 */
    @NotBlank(message = "Cron 表达式不能为空")
    private String cron;
}
