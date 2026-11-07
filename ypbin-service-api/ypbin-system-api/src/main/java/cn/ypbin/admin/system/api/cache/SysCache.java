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
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.dto.SysUserSocialDto;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.cloud.feign.support.FeignResponses;
import cn.ypbin.starter.core.util.SpringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * 系统域共享数据缓存（永久缓存 + 主动失效）。
 *
 * <p>auth/ai 等跨服务调用方经 Feign 查询系统数据时，先读缓存、未命中再走 Feign 并回填，
 * 避免每次登录/查询都触发 RPC。缓存**永不过期**，一致性由「主动失效」保证：
 * 数据变更时由 system 服务写操作显式调用 {@link #evictUser}/{@link #evictUserAuth} 清缓存，
 * 读侧下次访问自然回源——只要数据未变更，永远命中缓存。</p>
 *
 * <p>安全约束：用户缓存**不存密码**——缓存的载荷是不含 {@code password} 字段的只读视图
 * （{@code SysUserDto}），故「不落敏感字段」由类型结构保证，而非靠回填前手工置空；
 * 密码校验走 {@code ISystemClient.verifyPassword} 直查库比对，改密即时生效。</p>
 *
 * <p><strong>关于「缓存列表载荷」的类型表达（2026-10-19 已治本）</strong>：
 * 本类 3 处「缓存载荷是列表」的读取（角色码、权限码、绑定列表）此前必须写成
 * {@code (Class<List<T>>) (Class<?>) List.class} 双强转——根因是 starter 的
 * {@code CacheService#getOrLoad} 只接受 {@code Class<T>}，而 {@code List<String>} 的运行时类型信息已擦除。
 * 但该 {@code type} 参数在实现里<b>只做 {@code (T)} 强转、从不参与反序列化</b>（真正的类型还原由
 * {@code RedisTemplate} 的值序列化器按 JSON 内的类型信息完成）。</p>
 *
 * <p>starter 4.2.0 已补「<b>无需 Class 参数</b>」的重载
 * {@code getOrLoad(String key, Supplier<T> loader, Duration ttl)}，本类改用之后
 * <b>3 处双重强转与 {@code @SuppressWarnings("unchecked")} 全部移除</b>。
 * 此前那个「抽成 {@code uncheckedListType()} 收敛到一处」只是治标；现在根因已消除，
 * 后来人<b>不应</b>再引入双重强转（{@code SysCacheTest} 有源码级护栏钉住）。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public final class SysCache {

    /*
     * 为何用户/绑定快照的 key 带 v2：这三个 key 的历史载荷是**实体**（SysUser），
     * 而 CacheService#getOrLoad 对命中值是「无类型校验的强转」，且这些 key 是**永久缓存**——
     * 若沿用旧 key，升级后会读到旧实体对象并在强转处抛 ClassCastException（登录直接不可用）。
     * 因此载荷收窄为 DTO 的同时把 key 升到 v2：新代码读 v2、旧 v1 键自然失效不再被读取
     * （其残留可在首次部署后手动 DEL，非必需）。同类约束见 SysUserServiceImpl 的 @CacheEvict。
     */
    private static final String USERNAME_KEY = "sys:user:v2:username:";
    private static final String USER_ID_KEY = "sys:user:v2:id:";
    private static final String PHONE_KEY = "sys:user:v2:phone:";
    private static final String ROLE_USER_KEY = "sys:role:user:";
    private static final String PERM_USER_KEY = "sys:perm:user:";
    private static final String CONFIG_KEY = "sys:config:key:";
    private static final String SOCIAL_CONFIG_KEY = "sys:social:config:";
    private static final String SOCIAL_BINDING_KEY = "sys:social:v2:binding:";
    private static final String SOCIAL_BINDINGS_USER_KEY = "sys:social:v2:bindings:";
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
     * 按用户名取用户（登录用），永久缓存。缓存载荷为不含密码的只读视图。
     */
    public static @Nullable SysUserDto getUserByUsername(String username) {
        return CacheUtils.getOrLoad(
            USERNAME_KEY + username,
            SysUserDto.class,
            () -> FeignResponses.dataOrThrow(feignClient().getUserByUsername(username),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 按 ID 取用户，永久缓存。缓存载荷为不含密码的只读视图。
     */
    public static @Nullable SysUserDto getUserById(Long userId) {
        return CacheUtils.getOrLoad(
            USER_ID_KEY + userId,
            SysUserDto.class,
            () -> FeignResponses.dataOrThrow(feignClient().getUserById(userId),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 用户角色码（登录组装身份头用），永久缓存。
     */
    public static List<String> getUserRoleCodes(Long userId) {
        return CacheUtils.getOrLoad(
            ROLE_USER_KEY + userId,
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
        evictUserAuth(List.of(userId));
    }

    /**
     * 批量清除多个用户的角色/权限缓存（角色授权、菜单权限码、租户权限模板变更后调用）。
     *
     * <p>合并全部受影响用户的键后**只发一次**删除请求，避免按用户逐个清理造成 N 次缓存往返
     * （受影响用户数即读写放大倍数）。空集合短路，元素为 null 时跳过。</p>
     *
     * @param userIds 受影响用户 ID 集合，允许为 null/空
     */
    public static void evictUserAuth(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            keys.add(ROLE_USER_KEY + userId);
            keys.add(PERM_USER_KEY + userId);
        }
        if (!keys.isEmpty()) {
            CacheUtils.delete(keys);
        }
    }

    /**
     * 按手机号取用户（短信登录用），永久缓存。缓存载荷为不含密码的只读视图。
     */
    public static @Nullable SysUserDto getUserByPhone(String phone) {
        return CacheUtils.getOrLoad(
            PHONE_KEY + phone,
            SysUserDto.class,
            () -> FeignResponses.dataOrThrow(feignClient().getUserByPhone(phone),
                "系统服务暂不可用，请稍后重试"),
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
     * 批量清除系统参数缓存（分组更新等多键场景）。
     *
     * <p>合并全部键后只发一次删除请求，避免按参数逐条往返（参数分组常见十几项）。
     * 空集合短路，元素为 null 时跳过。</p>
     *
     * @param configKeys 参数键集合，允许为 null/空
     */
    public static void evictConfigs(Collection<String> configKeys) {
        if (configKeys == null || configKeys.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (String configKey : configKeys) {
            if (configKey != null) {
                keys.add(CONFIG_KEY + configKey);
            }
        }
        if (!keys.isEmpty()) {
            CacheUtils.delete(keys);
        }
    }

    /**
     * 批量清除导入用户的 username / phone 缓存键。
     *
     * <p>导入一次可能数百行，逐用户 {@link #evictUser} + {@link #evictUserByPhone}
     * 会放大成两倍行数的缓存往返；这里合并后一次删除。</p>
     *
     * @param usernames 用户名集合，允许为 null/空
     * @param phones    手机号集合，允许为 null/空
     */
    public static void evictUsersByUsernameAndPhone(Collection<String> usernames,
            Collection<String> phones) {
        List<String> keys = new ArrayList<>();
        if (usernames != null) {
            for (String username : usernames) {
                if (username != null) {
                    keys.add(USERNAME_KEY + username);
                }
            }
        }
        if (phones != null) {
            for (String phone : phones) {
                if (phone != null) {
                    keys.add(PHONE_KEY + phone);
                }
            }
        }
        if (!keys.isEmpty()) {
            CacheUtils.delete(keys);
        }
    }

    /**
     * 第三方登录平台授权配置（auth 构建授权请求用），永久缓存。
     */
    public static SocialAuthConfig getSocialAuthConfig(String source) {
        // 缓存键在读写两侧都必须是同一个口径：authorize 入口的 :source 来自 URL（可能是 "GitHub"），
        // 而写侧失效用的是小写归一值。若读取侧原样拼键，大小写变体就会各存一份永久缓存，
        // 平台停用后 "GitHub" 那份永远清不掉。故这里统一归一，再用于缓存键与回源参数。
        String normalizedSource = normalizeSocialSource(source);
        return CacheUtils.getOrLoad(
            SOCIAL_CONFIG_KEY + normalizedSource,
            SocialAuthConfig.class,
            () -> FeignResponses.dataOrThrow(feignClient().getSocialAuthConfig(normalizedSource),
                "系统服务暂不可用，请稍后重试"),
            null);
    }

    /**
     * 清除第三方登录平台授权配置缓存（平台配置变更后调用）。
     *
     * <p>{@code source} 与读取侧同口径（去空白 + 小写）：本方法内部再做一次归一，
     * 调用方传 {@code GitHub} 也能清到 {@code github} 的键。</p>
     *
     * @param source 平台标识
     */
    public static void evictSocialConfig(String source) {
        if (source != null) {
            CacheUtils.delete(SOCIAL_CONFIG_KEY + normalizeSocialSource(source));
        }
    }

    /** 第三方平台标识归一：去空白 + 小写（缓存键的单一口径）。 */
    private static String normalizeSocialSource(@Nullable String source) {
        return source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 按平台与 openId 查第三方绑定（第三方登录用），永久缓存。
     */
    public static @Nullable SysUserSocialDto getSocialBinding(String platform, String openId) {
        return CacheUtils.getOrLoad(
            SOCIAL_BINDING_KEY + platform + ":" + openId,
            SysUserSocialDto.class,
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
    public static List<SysUserSocialDto> listSocialBindings(Long userId) {
        return CacheUtils.getOrLoad(
            SOCIAL_BINDINGS_USER_KEY + userId,
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
}
