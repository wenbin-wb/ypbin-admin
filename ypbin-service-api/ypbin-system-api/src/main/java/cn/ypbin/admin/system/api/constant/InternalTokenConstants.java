/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.api.constant;

/**
 * system-svc 内部端点（{@code /internal/**}）调用凭证共享常量。
 *
 * <p>凭证携带侧（{@code InternalTokenFeignConfiguration}）与凭证守卫侧
 * （{@code InternalTokenGuardInterceptor}）分别位于不同模块，头名与配置键在此单一定义，
 * 两侧统一引用，避免字面量漂移导致守卫放行/携带不一致。</p>
 *
 * @author wenbin
 * @since 2026-09-09
 */
public final class InternalTokenConstants {

    /** 内部调用凭证配置键（各服务经环境变量注入同一值） */
    public static final String TOKEN_PROPERTY = "ypbin.internal.token";

    /** 内部调用凭证请求头名（Feign 携带侧与 system-svc 守卫侧一致） */
    public static final String TOKEN_HEADER = "X-Internal-Token";

    private InternalTokenConstants() {
    }
}
