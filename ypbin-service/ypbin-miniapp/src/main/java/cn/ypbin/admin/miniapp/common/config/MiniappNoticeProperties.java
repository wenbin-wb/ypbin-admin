/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 小程序公告配置。
 *
 * @author wenbin
 * @since 2026-11-15
 */
@Getter
@Setter
@ConfigurationProperties(prefix = MiniappNoticeProperties.PREFIX)
public class MiniappNoticeProperties {

    public static final String PREFIX = "ypbin.miniapp.notice";

    /** 公告投放客户端 ID（sys_client.client_id）；与小程序登录客户端保持一致 */
    private String clientId = "miniapp";
}
