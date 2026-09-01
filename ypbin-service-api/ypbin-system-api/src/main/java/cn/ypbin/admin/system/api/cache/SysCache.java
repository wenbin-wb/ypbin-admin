/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.api.cache;

import cn.ypbin.admin.system.api.feign.SystemPermissionFeignClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.util.SpringUtils;
import java.time.Duration;

/**
 * 系统域共享数据缓存（对齐 blade 的 SysCache 模式）。
 *
 * <p>auth/ai 等跨服务调用方经 Feign 查询系统数据时，先读缓存、未命中再走 Feign 并回填，
 * 避免每次登录/查询都触发 RPC。缓存键与 TTL 集中定义于此，单侧维护。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public final class SysCache {

    /** 缓存 TTL：用户信息 5 分钟（登录高频，容忍短暂滞后） */
    private static final Duration USER_TTL = Duration.ofMinutes(5);

    private static final String USERNAME_KEY = "sys:user:username:";
    private static final String USER_ID_KEY = "sys:user:id:";

    private static volatile SystemPermissionFeignClient feignClient;

    private SysCache() {
    }

    private static SystemPermissionFeignClient feignClient() {
        if (feignClient == null) {
            synchronized (SysCache.class) {
                if (feignClient == null) {
                    feignClient = SpringUtils.getBean(SystemPermissionFeignClient.class);
                }
            }
        }
        return feignClient;
    }

    /**
     * 按用户名取用户（登录用），带缓存。
     */
    public static SysUser getUserByUsername(String username) {
        return CacheUtils.getOrLoad(
            USERNAME_KEY + username,
            SysUser.class,
            () -> {
                var result = feignClient().getUserByUsername(username);
                return result == null ? null : result.getData();
            },
            USER_TTL);
    }

    /**
     * 按 ID 取用户，带缓存。
     */
    public static SysUser getUserById(Long userId) {
        return CacheUtils.getOrLoad(
            USER_ID_KEY + userId,
            SysUser.class,
            () -> {
                var result = feignClient().getUserById(userId);
                return result == null ? null : result.getData();
            },
            USER_TTL);
    }

    /**
     * 清除用户缓存（用户信息变更后由 system 服务调用）。
     */
    public static void evictUser(Long userId, String username) {
        if (userId != null) {
            CacheUtils.delete(USER_ID_KEY + userId);
        }
        if (username != null) {
            CacheUtils.delete(USERNAME_KEY + username);
        }
    }
}
