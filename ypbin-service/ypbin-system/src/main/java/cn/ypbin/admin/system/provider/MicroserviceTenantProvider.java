/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 系统服务租户来源：从网关签发的身份头租户上下文取租户 ID。
 *
 * @author wenbin
 * @since 2026-09-03
 */
@Component
public class MicroserviceTenantProvider implements TenantProvider {

    @Override
    public Optional<Long> getCurrentTenantId() {
        return IdentityContext.getTenantId();
    }
}
