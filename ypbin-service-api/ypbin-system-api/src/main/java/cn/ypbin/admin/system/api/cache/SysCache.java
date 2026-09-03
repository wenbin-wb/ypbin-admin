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

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.cloud.feign.support.FeignResponses;
import cn.ypbin.starter.core.util.SpringUtils;
import java.util.List;

/**
 * 系统域共享数据缓存（永久缓存 + 主动失效）。
 *
 * <p>auth/ai 等跨服务调用方经 Feign 查询系统数据时，先读缓存、未命中再走 Feign 并回填，
 * 避免每次登录/查询都触发 RPC。缓存**永不过期**，一致性由「主动失效」保证：
 * 数据变更时由 system 服务写操作显式调用 {@link #evictUser}/{@link #evictUserAuth} 清缓存，
 * 读侧下次访问自然回源——只要数据未变更，永远命中缓存。</p>
 *
 * <p>安全约束：用户缓存**不存密码**（回填前 {@code password} 置空），密码校验走
 * {@code ISystemClient.verifyPassword} 直查库比对，改密即时生效、缓存无敏感字段。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public final class SysCache {

    private static final String USERNAME_KEY = "sys:user:username:";
    private static final String USER_ID_KEY = "sys:user:id:";
    private static final String PHONE_KEY = "sys:user:phone:";
    private static final String ROLE_USER_KEY = "sys:role:user:";
    private static final String PERM_USER_KEY = "sys:perm:user:";
    private static final String CONFIG_KEY = "sys:config:key:";
    private static final String SOCIAL_CONFIG_KEY = "sys:social:config:";
    private static final String SOCIAL_CONFIGS_KEY = "sys:social:configs";
    private static final String SOCIAL_BINDING_KEY = "sys:social:binding:";
    private static final String SOCIAL_BINDINGS_USER_KEY = "sys:social:bindings:";
    private static final String SOCIAL_BOUND_KEY = "sys:social:bound:";
    private static final String SOCIAL_ACCOUNT_BOUND_KEY = "sys:social:account-bound:";

    private static volatile ISystemClient feignClient;

    private SysCache() {
    }

    private static ISystemClient feignClient() {
        if (feignClient == null) {
            synchronized (SysCache.class) {
                if (feignClient == null) {
                    feignClient = SpringUtils.getBean(ISystemClient.class);
                }
            }
        }
        return feignClient;
    }

    /**
     * 按用户名取用户（登录用），永久缓存。回填时密码置空，缓存不落敏感字段。
     */
    public static SysUser getUserByUsername(String username) {
        return CacheUtils.getOrLoad(
            USERNAME_KEY + username,
            SysUser.class,
            () -> sanitize(FeignResponses.dataOrThrow(feignClient().getUserByUsername(username),
                "系统服务暂不可用，请稍后重试")),
            null);
    }

    /**
     * 按 ID 取用户，永久缓存。回填时密码置空。
     */
    public static SysUser getUserById(Long userId) {
        return CacheUtils.getOrLoad(
            USER_ID_KEY + userId,
            SysUser.class,
            () -> sanitize(FeignResponses.dataOrThrow(feignClient().getUserById(userId),
                "系统服务暂不可用，请稍后重试")),
            null);
    }

    /**
     * 用户角色码（登录组装身份头用），永久缓存。
     */
    public static List<String> getUserRoleCodes(Long userId) {
        return CacheUtils.getOrLoad(
            ROLE_USER_KEY + userId,
            (Class<List<String>>) (Class<?>) List.class,
            () -> FeignResponses.dataOrThrow(feignClient().listRoleCodes(userId),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 用户权限码（鉴权用），永久缓存。
     */
    public static List<String> getUserPermissions(Long userId) {
        return CacheUtils.getOrLoad(
            PERM_USER_KEY + userId,
            (Class<List<String>>) (Class<?>) List.class,
            () -> FeignResponses.dataOrThrow(feignClient().listPermissions(userId),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 清除用户基础信息缓存（用户资料/状态/密码变更后由 system 服务写操作调用）。
     *
     * @param userId   用户 ID（可为 null）
     * @param username 用户名（可为 null；改用户名时传旧名，两个 key 都清）
     */
    public static void evictUser(Long userId, String username) {
        if (userId != null) {
            CacheUtils.delete(USER_ID_KEY + userId);
        }
        if (username != null) {
            CacheUtils.delete(USERNAME_KEY + username);
        }
    }

    /**
     * 清除用户角色/权限缓存（角色分配、权限变更后调用）。
     *
     * @param userId 用户 ID
     */
    public static void evictUserAuth(Long userId) {
        if (userId == null) {
            return;
        }
        CacheUtils.delete(List.of(ROLE_USER_KEY + userId, PERM_USER_KEY + userId));
    }

    /**
     * 按手机号取用户（短信登录用），永久缓存。回填时密码置空。
     */
    public static SysUser getUserByPhone(String phone) {
        return CacheUtils.getOrLoad(
            PHONE_KEY + phone,
            SysUser.class,
            () -> sanitize(FeignResponses.dataOrThrow(feignClient().getUserByPhone(phone),
                "系统服务暂不可用，请稍后重试")),
            null);
    }

    /**
     * 清除手机号用户缓存（用户手机号变更后调用）。
     *
     * @param phone 手机号（可为 null）
     */
    public static void evictUserByPhone(String phone) {
        if (phone != null) {
            CacheUtils.delete(PHONE_KEY + phone);
        }
    }

    /**
     * 按参数键取系统参数（登录开关/短信/邮件配置等），永久缓存。
     */
    public static ConfigValue getConfigByKey(String configKey) {
        return CacheUtils.getOrLoad(
            CONFIG_KEY + configKey,
            ConfigValue.class,
            () -> FeignResponses.dataOrThrow(feignClient().getConfigByKey(configKey),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 清除系统参数缓存（参数变更后调用）。
     */
    public static void evictConfig(String configKey) {
        if (configKey != null) {
            CacheUtils.delete(CONFIG_KEY + configKey);
        }
    }

    /**
     * 第三方登录平台授权配置（auth 构建授权请求用），永久缓存。
     */
    public static SocialAuthConfig getSocialAuthConfig(String source) {
        return CacheUtils.getOrLoad(
            SOCIAL_CONFIG_KEY + source,
            SocialAuthConfig.class,
            () -> FeignResponses.dataOrThrow(feignClient().getSocialAuthConfig(source),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 全部启用第三方登录平台授权配置，永久缓存。
     */
    public static List<SocialAuthConfig> listSocialAuthConfigs() {
        return CacheUtils.getOrLoad(
            SOCIAL_CONFIGS_KEY,
            (Class<List<SocialAuthConfig>>) (Class<?>) List.class,
            () -> FeignResponses.dataOrThrow(feignClient().listSocialAuthConfigs(),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 清除第三方登录平台授权配置缓存（平台配置变更后调用）。
     */
    public static void evictSocialConfig(String source) {
        if (source != null) {
            CacheUtils.delete(SOCIAL_CONFIG_KEY + source);
        }
        CacheUtils.delete(SOCIAL_CONFIGS_KEY);
    }

    /**
     * 按平台与 openId 查第三方绑定（第三方登录用），永久缓存。
     */
    public static SysUserSocial getSocialBinding(String platform, String openId) {
        return CacheUtils.getOrLoad(
            SOCIAL_BINDING_KEY + platform + ":" + openId,
            SysUserSocial.class,
            () -> FeignResponses.dataOrThrow(feignClient().getSocialBinding(platform, openId),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 用户是否已绑定指定平台，永久缓存。
     */
    public static boolean isSocialUserBound(Long userId, String platform) {
        return CacheUtils.getOrLoad(
            SOCIAL_BOUND_KEY + userId + ":" + platform,
            Boolean.class,
            () -> FeignResponses.dataOrThrow(feignClient().isSocialUserBound(userId, platform),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 按平台与 openId 是否已绑定其他用户，永久缓存。
     */
    public static boolean isSocialAccountBound(String platform, String openId) {
        return CacheUtils.getOrLoad(
            SOCIAL_ACCOUNT_BOUND_KEY + platform + ":" + openId,
            Boolean.class,
            () -> FeignResponses.dataOrThrow(feignClient().isSocialAccountBound(platform, openId),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 用户已绑定的平台列表，永久缓存。
     */
    public static List<SysUserSocial> listSocialBindings(Long userId) {
        return CacheUtils.getOrLoad(
            SOCIAL_BINDINGS_USER_KEY + userId,
            (Class<List<SysUserSocial>>) (Class<?>) List.class,
            () -> FeignResponses.dataOrThrow(feignClient().listSocialBindings(userId),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 清除第三方绑定缓存（绑定/解绑后调用；openId 为空时按用户清全部）。
     */
    public static void evictSocialBinding(Long userId, String platform, String openId) {
        if (platform != null && openId != null) {
            CacheUtils.delete(SOCIAL_BINDING_KEY + platform + ":" + openId);
            CacheUtils.delete(SOCIAL_ACCOUNT_BOUND_KEY + platform + ":" + openId);
        }
        if (userId != null && platform != null) {
            CacheUtils.delete(SOCIAL_BOUND_KEY + userId + ":" + platform);
        }
        if (userId != null) {
            CacheUtils.delete(SOCIAL_BINDINGS_USER_KEY + userId);
        }
    }

    /**
     * 缓存回填前脱敏：密码置空（缓存不落敏感字段，改密即时生效）。
     */
    private static SysUser sanitize(SysUser user) {
        if (user == null) {
            return null;
        }
        user.setPassword(null);
        return user;
    }
}
