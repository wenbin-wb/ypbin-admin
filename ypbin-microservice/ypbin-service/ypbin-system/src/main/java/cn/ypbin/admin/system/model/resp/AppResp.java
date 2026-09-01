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
 * 开放应用响应。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Getter
@Setter
public class AppResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String accessKey;

    private String appName;

    private LocalDateTime expireTime;

    private Integer enabled;

    private LocalDateTime createTime;
}
