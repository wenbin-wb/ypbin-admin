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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 临时封禁请求。
 *
 * @author wenbin
 * @since 2026-11-05
 */
@Getter
@Setter
public class AccessBlocklistBanReq {

    /** 客户端 IP */
    @NotBlank(message = "IP 不能为空")
    private String clientIp;

    /** 封禁时长（秒），最长 30 天 */
    @Min(value = 60, message = "封禁时长不能少于 60 秒")
    @Max(value = 2_592_000, message = "封禁时长不能超过 30 天")
    private long banSeconds;

    /** 封禁原因 */
    @Size(max = 255, message = "原因不能超过 255 个字符")
    private String reason;
}
