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

    private String remark;

    private LocalDateTime createTime;
}
