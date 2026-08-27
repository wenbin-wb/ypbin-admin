/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.core;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 匿名公开接口（分享/挂件）限流配置。
 *
 * <p>绑定 {@code ypbin.ai.rate-limit} 配置段，默认开启：每个客户端 IP 在
 * {@code windowSeconds} 秒窗口内最多 {@code limit} 次请求（按场景独立计数）。</p>
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ypbin.ai.rate-limit")
public class AiRateLimitProperties {

    /** 是否启用限流 */
    private boolean enabled = true;

    /** 窗口内允许的最大请求数 */
    private int limit = 10;

    /** 窗口时长（秒） */
    private int windowSeconds = 60;
}
