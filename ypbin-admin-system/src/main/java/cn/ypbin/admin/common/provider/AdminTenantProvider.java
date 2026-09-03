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
 * <p>无登录且未显式绑定租户时返回空，行级租户拦截器按 {@code tenant_id = NULL}
 * fail-closed，不会放开租户边界。后台任务必须显式绑定租户，跨租户扫描必须明确使用
 * {@code TenantContext.executeIgnore}。</p>
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
