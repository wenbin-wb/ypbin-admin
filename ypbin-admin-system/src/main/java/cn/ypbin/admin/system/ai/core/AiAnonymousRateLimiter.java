/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.core;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 匿名公开接口内存限流器（固定窗口计数）。
 *
 * <p>按 {@code key}（如 客户端 IP + 场景）在窗口内计数，超过 {@code limit} 拒绝。
 * 内存实现、进程内生效，适合单实例部署；多实例需换分布式限流（Redis）。
 * 惰性清理过期窗口，防止 Map 无限膨胀。</p>
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Component
@RequiredArgsConstructor
public class AiAnonymousRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AiAnonymousRateLimiter.class);

    private static final int MAX_BUCKETS = 10_000;

    /** key -> [windowStartEpochSec, count] */
    private final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

    private final AiRateLimitProperties props;

    @PostConstruct
    void logConfig() {
        log.info("[ypbin-ai] 匿名限流已加载: enabled={}, limit={}/{}s",
            props.isEnabled(), props.getLimit(), props.getWindowSeconds());
    }

    /**
     * 尝试获取一次配额。
     *
     * @param key 限流键（如 "share:ask:1.2.3.4"）
     * @return true 允许，false 超过限流
     */
    public boolean tryAcquire(String key) {
        if (!props.isEnabled()) {
            return true;
        }
        long now = System.currentTimeMillis() / 1000;
        int window = props.getWindowSeconds() > 0 ? props.getWindowSeconds() : 60;
        int limit = props.getLimit() > 0 ? props.getLimit() : 10;
        long[] bucket = buckets.compute(key, (k, v) -> {
            if (v == null || now - v[0] >= window) {
                return new long[] {now, 1};
            }
            v[1]++;
            return v;
        });
        if (buckets.size() > MAX_BUCKETS) {
            evictExpired(now, window);
        }
        return bucket[1] <= limit;
    }

    /** 清理过期窗口，控制内存占用 */
    private void evictExpired(long now, int window) {
        Iterator<Map.Entry<String, long[]>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, long[]> entry = it.next();
            if (now - entry.getValue()[0] >= window) {
                it.remove();
            }
        }
    }
}
