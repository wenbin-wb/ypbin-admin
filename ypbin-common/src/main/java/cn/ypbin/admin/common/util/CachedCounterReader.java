/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缓存计数器读取（短信验证码失败次数、公开访问密码失败次数等共用）。
 *
 * <p><b>为什么必须只有一份</b>：缓存反序列化出的运行时类型不稳定（{@code Integer}/{@code Long}/
 * 字符串都可能，取决于序列化器与写入端），各处自行 {@code instanceof} 解析会漂移——
 * 序列化实现一变，只修好一处的话，另一处的失败计数会静默恒为 0，锁定阈值直接失效。
 * 这正是本仓把「SOCIAL_* 配置解析」收敛成单一来源的同一类问题。</p>
 *
 * <p>解析失败返回 0 并记 WARN，而不是抛异常：计数器损坏不该让整条登录/访问链路不可用，
 * 但「计数没生效」必须留下痕迹可查。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
public final class CachedCounterReader {

    private static final Logger log = LoggerFactory.getLogger(CachedCounterReader.class);

    private CachedCounterReader() {
    }

    /**
     * 读取缓存中的计数值。
     *
     * @param cached 缓存原值（可空）
     * @return 计数值；空值或无法解析时返回 0
     */
    public static long read(Object cached) {
        if (cached == null) {
            return 0L;
        }
        if (cached instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(cached.toString());
        } catch (NumberFormatException e) {
            log.warn("[ypbin] 缓存计数无法解析，按 0 处理：value={}", cached);
            return 0L;
        }
    }
}
