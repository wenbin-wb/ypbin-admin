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
 * 授权交付信息。
 *
 * <p>签发交付给被授权方的全部凭据：内联授权码（CODE 交付展示；FILE 交付由下载接口输出，本字段同样可用）
 * 与联机开放应用密钥（审批通过时按被授权方自动创建或复用，供消费端配置联机校验）。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
@Data
public class LicenseDeliveryResp {

    /** 授权串（Base64） */
    private String authCode;

    /** 联机开放应用 ID（可为空表示未建联机应用） */
    private Long appId;

    /** 联机应用名称（= 被授权方） */
    private String appName;

    /** 联机应用 Access Key */
    private String accessKey;

    /** 联机应用 Secret Key（交付给消费端配置 online.secret-key） */
    private String secretKey;
}
