/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.tracking;

import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tracking.core.TrackIdentityProvider;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 埋点登录身份提供者（starter 的 {@link TrackIdentityProvider} 扩展点实现）。
 *
 * <p><strong>为什么只能在请求线程上取值</strong>：身份由网关校验 token 后签发可信身份头注入，
 * starter 的 {@link IdentityContext} 再把这份身份写入<b>请求线程</b>的 ThreadLocal；
 * 埋点是异步落库的——采集侧在请求线程上取值并随事件带下去，之后由消费者线程写库。
 * 消费者线程没有请求上下文、也没有身份头，在那里读 {@link IdentityContext} 只会拿到空值，
 * 所以本类必须（也只应）在请求线程上被调用。</p>
 *
 * <p>starter 以 {@code @ConditionalOnMissingBean} 注册缺省实现 {@link TrackIdentityProvider#NONE}，
 * 本类作为宿主 Bean 将其覆盖。</p>
 *
 * <p><strong>匿名是主流场景</strong>：采集端点对匿名请求放行，未登录时两个维度均应返回 {@code null}，
 * 绝不抛异常——身份取不到只意味着事件缺少分组维度，不该让上报失败。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Slf4j
@Component
public class AdminTrackIdentityProvider implements TrackIdentityProvider {

    /** 身份维度：登录用户。 */
    private static final String DIMENSION_USER = "userId";

    /** 身份维度：租户。 */
    private static final String DIMENSION_TENANT = "tenantId";

    @Override
    public @Nullable Long userId() {
        return resolve(DIMENSION_USER, IdentityContext::getUserId);
    }

    @Override
    public @Nullable Long tenantId() {
        return resolve(DIMENSION_TENANT, IdentityContext::getTenantId);
    }

    /**
     * 从当前线程的身份上下文取值。
     *
     * <p>取不到身份不是错误，返回 {@code null} 即可；但若上下文读取本身抛异常（如线程内被污染），
     * 记一条带完整堆栈的告警——事件缺少该维度是可见的，不做无声吞掉。</p>
     *
     * @param dimension 维度名（仅用于日志定位）
     * @param accessor  该维度的取值函数
     * @return 维度值；无身份或取值失败时为 {@code null}
     */
    private @Nullable Long resolve(String dimension, Supplier<Optional<Long>> accessor) {
        try {
            return accessor.get().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("[ypbin-admin] tracking identity provider failed to resolve {}, "
                + "event will be stored without that dimension.", dimension, ex);
            return null;
        }
    }
}
