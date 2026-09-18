/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.log;

import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.util.Optional;

/**
 * 操作人数据源（网关身份头实现）：从 {@link IdentityContext} 取当前用户。
 *
 * <p>与 {@code DbLogProviders.IdentityLogUserProvider} 语义一致，区别是<b>装配位置</b>：
 * 本类随 {@link RemoteLogAutoConfiguration} 装配，供 auth/ai 这类"没有本地落库实现"的服务使用；
 * system 由自己的 {@code DbLogProviders} 提供，二者靠 {@code @ConditionalOnMissingBean} 互斥，
 * 同一容器内只会存在一个 {@link LogUserProvider}。</p>
 *
 * <p>取用户在<b>业务线程</b>完成（{@code LogCollector.collect} 在方法返回后的 finally 里调用本类），
 * 不依赖异步线程的 ThreadLocal，故异步落库不会丢操作人。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public class IdentityHeaderLogUserProvider implements LogUserProvider {

    @Override
    public Optional<Long> getCurrentUserId() {
        return IdentityContext.getUserId();
    }
}
