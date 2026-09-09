/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部调用凭证配置。
 *
 * <p>微服务 Feign 直连（不经网关）访问 system-svc 的 {@code /internal/**} 内部端点时，
 * 须携带与配置一致的凭证头（{@code X-Internal-Token}），否则内部端点对任意登录用户开放会带来
 * 跨租户检索、密钥明文出网与口令爆破风险。token 属密钥，部署时经环境变量注入（同名键），不落库。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@ConfigurationProperties(prefix = InternalProperties.PREFIX)
public class InternalProperties {

    public static final String PREFIX = "ypbin.internal";

    /** 内部调用凭证（各服务共享同一值，优先环境变量注入） */
    private String token;
}
