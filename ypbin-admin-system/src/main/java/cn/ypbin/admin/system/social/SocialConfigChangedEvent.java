/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.social;

import me.zhyd.oauth.request.AuthRequest;

/**
 * 第三方登录配置提交完成后的同步事件。
 *
 * @param source 平台标识
 * @param enabled 是否启用
 * @param request 已校验的授权请求
 * @author wenbin
 * @since 2026-08-08
 */
public record SocialConfigChangedEvent(String source, boolean enabled, AuthRequest request) {
}
