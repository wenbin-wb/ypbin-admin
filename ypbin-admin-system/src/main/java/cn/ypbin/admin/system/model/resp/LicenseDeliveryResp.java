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
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 授权交付信息。
 *
 * <p>提供可重复读取的授权码与联机应用公开标识，不返回应用密钥。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
@Data
public class LicenseDeliveryResp {

    /** 授权串（Base64） */
    private String authCode;

    /** 联机开放应用 ID（可为空表示未建联机应用） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    /** 联机应用名称（= 被授权方） */
    private String appName;

    /** 联机应用 Access Key */
    private String accessKey;
}
