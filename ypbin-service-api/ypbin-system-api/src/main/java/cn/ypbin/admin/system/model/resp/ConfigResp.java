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

import cn.ypbin.starter.json.ref.RefText;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统参数响应。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class ConfigResp {

    private Long id;

    private String configGroup;

    private String name;

    private String configKey;

    private String configValue;

    private Integer builtIn;

    private String remark;

    @RefText("user")
    private Long createUser;

    private LocalDateTime updateTime;
}
