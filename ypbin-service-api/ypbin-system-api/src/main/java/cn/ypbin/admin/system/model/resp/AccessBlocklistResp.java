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

/**
 * 访问黑名单规则响应。
 *
 * @author wenbin
 * @since 2026-11-05
 */
@Getter
@Setter
public class AccessBlocklistResp {

    private Long id;

    private String matchType;

    private String matchValue;

    private String reason;

    private LocalDateTime expireTime;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
