/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.usage;

import cn.ypbin.admin.ai.entity.AiUsageLog;
import cn.ypbin.admin.ai.mapper.AiUsageLogMapper;
import cn.ypbin.starter.tenant.core.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 用量日志落库器（单条 INSERT）。
 *
 * <p><b>为什么单独一个 Bean</b>：{@code @Transactional} 依赖代理，同类内自调用不生效；
 * 把落库放在这里、由 {@link AdminAiUsageListener} 跨 Bean 调用，事务才真正生效。</p>
 *
 * <p><b>为什么显式走 {@code TenantContext.executeIgnore}</b>：用量回调由 starter 在 Reactor 终局信号上
 * 触发，回调线程可能没有请求级租户上下文，而租户拦截器在缺上下文时是 fail-closed（直接抛
 * 「缺少租户上下文」）。本方法的租户归属由实体自身显式携带（{@link AiUsageLog#getTenantId()}），
 * 拦截器不再注入列；若该值意外为空，数据库 {@code tenant_id NOT NULL} 会直接拒绝，
 * 不会静默写到别的租户下。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@Component
@RequiredArgsConstructor
public class AiUsageLogWriter {

    private final AiUsageLogMapper usageLogMapper;

    /**
     * 落库一条用量日志。
     *
     * @param entity 已补齐租户/用户维度的用量日志（调用方保证租户非空）
     */
    @Transactional(rollbackFor = Exception.class)
    public void write(AiUsageLog entity) {
        TenantContext.runIgnore(() -> usageLogMapper.insert(entity));
    }
}
