/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.resp;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录客户端响应。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Getter
@Setter
public class ClientResp {

    private Long id;

    private String clientId;

    private String clientType;

    private String authTypes;

    private Long timeout;

    private Long activeTimeout;

    private Integer concurrentEnabled;

    private Integer maxLoginCount;

    /** 状态：1 启用、0 禁用 */
    private Integer status;

    /** 是否已配置客户端密钥（只返回标志，不返回密钥本身） */
    private Boolean secretConfigured;

    /** 是否为配置的默认客户端（未声明客户端身份的请求会回落到它，禁止停用/改 ID/删除/配密钥） */
    private Boolean defaultClient;

    private String remark;

    private LocalDateTime createTime;
}
