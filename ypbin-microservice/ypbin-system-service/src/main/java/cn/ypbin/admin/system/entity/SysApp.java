/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 开放平台应用。全局表，不隔离租户。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_app")
public class SysApp extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Access Key */
    private String accessKey;

    /** Secret Key */
    private String secretKey;

    /** 应用名称 */
    private String appName;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 是否启用 */
    private Integer enabled;
}
