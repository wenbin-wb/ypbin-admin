/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.model.resp;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 模型配置响应 DTO（API Key 脱敏）。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
public class AiModelConfigResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;
    private String provider;
    private String modelType;
    private String baseUrl;
    private String modelName;
    private Integer isDefault;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    /** API Key 脱敏：只返回前 6 位 + **** */
    private String apiKeyMasked;
}
