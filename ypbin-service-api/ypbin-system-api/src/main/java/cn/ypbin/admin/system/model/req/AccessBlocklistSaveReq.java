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
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 访问黑名单新增/修改请求。
 *
 * @author wenbin
 * @since 2026-11-05
 */
@Getter
@Setter
public class AccessBlocklistSaveReq {

    /** 匹配维度：IP/CIDR/USER_AGENT/PATH */
    @NotBlank(message = "匹配类型不能为空")
    private String matchType;

    /** 匹配值 */
    @NotBlank(message = "匹配值不能为空")
    @Size(max = 255, message = "匹配值不能超过 255 个字符")
    private String matchValue;

    /** 命中原因 */
    @Size(max = 255, message = "原因不能超过 255 个字符")
    private String reason;

    /** 到期时间；为空表示长期有效 */
    private LocalDateTime expireTime;

    /** 状态：1 启用、0 停用 */
    private Integer status;
}
