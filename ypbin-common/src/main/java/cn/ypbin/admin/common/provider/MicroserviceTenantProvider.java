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

import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import java.util.Optional;

/**
 * 微服务租户来源：从网关签发的身份头租户上下文取租户 ID。
 *
 * <p>单体版从 sa-token 会话取租户；微服务版由网关校验 token 后签发
 * {@code X-Tenant-Id}，下游服务经 {@link IdentityHeaderFilter} 写入
 * {@link IdentityContext}，因此这里直接读取 {@link IdentityContext#getTenantId()}。</p>
 *
 * @author wenbin
 * @since 2026-09-03
 */
public class MicroserviceTenantProvider implements TenantProvider {

    @Override
    public Optional<Long> getCurrentTenantId() {
        return IdentityContext.getTenantId();
    }
}
