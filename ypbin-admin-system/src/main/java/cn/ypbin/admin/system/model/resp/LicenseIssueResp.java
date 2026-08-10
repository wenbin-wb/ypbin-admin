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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 授权签发结果。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Data
public class LicenseIssueResp {

    private String authCode;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    private String appName;

    private String accessKey;

    private String secretKey;
}
