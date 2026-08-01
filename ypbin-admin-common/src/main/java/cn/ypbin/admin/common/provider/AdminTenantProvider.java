/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.provider;

import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 租户来源：从当前登录用户的会话上下文取租户 ID。
 *
 * <p>未登录时（登录接口本身、定时任务等）返回空，此时行级租户拦截器不追加租户条件——
 * 登录按全局唯一的用户名查人，登录后查角色/权限走 {@code TenantContext.executeIgnore}。
 * 需要显式跨租户的场景用 {@code @TenantIgnore} 或 {@code TenantContext} 处理。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Component
public class AdminTenantProvider implements TenantProvider {

    @Override
    public Optional<Long> getCurrentTenantId() {
        return UserContext.getLoginUser().map(u -> u.getTenantId());
    }
}
