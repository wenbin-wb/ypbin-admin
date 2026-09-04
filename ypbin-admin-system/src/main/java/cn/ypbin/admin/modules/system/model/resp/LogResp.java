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

import cn.ypbin.starter.json.ref.RefText;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统日志响应。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class LogResp {

    private Long id;

    private String description;

    private String module;

    private String requestMethod;

    private String requestUri;

    private Integer statusCode;

    private String ip;

    private String location;

    private String browser;

    private String os;

    private String clientType;

    @RefText("user")
    private Long operateUserId;

    private LocalDateTime operateTime;

    private Long timeTaken;

    private Integer success;

    private String errorMsg;
}
