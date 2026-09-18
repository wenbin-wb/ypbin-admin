/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.support;

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.starter.security.core.PermissionProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * auth 服务的权限数据源（starter 端口 {@link PermissionProvider} 的宿主实现）。
 *
 * <p><strong>为什么必须有本类</strong>：starter 在宿主未提供 {@link PermissionProvider} 时装配
 * 返回空列表的默认实现，auth 侧 {@code @SaCheckPermission} 标注的接口会一律判定为无权限。
 * auth 不依赖 {@code ypbin-starter-data}、也不允许直连共享库，故与 ai 同口径经
 * {@link SysCache} 复用 system 的唯一一份判定实现（权限码唯一来源是
 * {@code sys_role_menu} → {@code sys_menu.auth_code}）。</p>
 *
 * <p><strong>为什么走 SysCache 而不是每次直接 Feign</strong>：注解鉴权在每个请求上取权限码，
 * 直连 Feign 会把鉴权变成每次一次 RPC；{@code SysCache} 命中 Redis 即返回、未命中才回源 system 并回填。
 * 缓存**永不过期**，一致性由 system 写路径的主动失效保证（角色授权/菜单权限码/租户权限模板变更后
 * 清 {@code sys:role:user:*}、{@code sys:perm:user:*}）。</p>
 *
 * <p><strong>失败语义（fail-closed，禁静默降级）</strong>：
 * ① system 不可达或业务失败：{@code SysCache} 抛 {@code BusinessException}，本类**不捕获** ⇒ 鉴权不通过
 * （拒绝）且故障显式可见，不会被伪装成「无权限 403」或「放行」；<br>
 * ② 响应成功但数据缺失（null）：记 ERROR 后返回空集合（拒绝）；<br>
 * ③ loginId 缺失或非法：记 WARN 后返回空集合（拒绝），不发起 RPC。</p>
 *
 * <p>本类与 ai 服务的同名实现刻意各留一份：两者是不同的部署单元，需要各自的宿主 Bean；
 * 判定语义只有 system 一份实现，这里只是薄适配，不含任何权限规则。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Component
public class AuthPermissionProvider implements PermissionProvider {

    private static final Logger log = LoggerFactory.getLogger(AuthPermissionProvider.class);

    /**
     * 查询账号的权限码集合。
     *
     * @param loginId   登录账号标识（Sa-Token 的 loginId，本仓为 userId 的十进制字符串）
     * @param loginType 账号体系类型
     * @return 权限码列表；无法确定权限时为空集合（拒绝）
     */
    @Override
    public List<String> getPermissions(Object loginId, String loginType) {
        Long userId = resolveUserId(loginId, loginType);
        if (userId == null) {
            return List.of();
        }
        List<String> permissions = SysCache.getUserPermissions(userId);
        if (permissions == null) {
            log.error("[auth] 权限码查询结果缺失，按拒绝处理（未放行）：userId={}", userId);
            return List.of();
        }
        return permissions;
    }

    /**
     * 查询账号的角色码集合。
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型
     * @return 角色码列表；无法确定身份时为空集合（拒绝）
     */
    @Override
    public List<String> getRoles(Object loginId, String loginType) {
        Long userId = resolveUserId(loginId, loginType);
        if (userId == null) {
            return List.of();
        }
        List<String> roleCodes = SysCache.getUserRoleCodes(userId);
        if (roleCodes == null) {
            log.error("[auth] 角色码查询结果缺失，按无角色处理（未放行）：userId={}", userId);
            return List.of();
        }
        return roleCodes;
    }

    /**
     * 把 Sa-Token 的 loginId 解析为用户 ID；解析失败返回 {@code null}，由调用方转成拒绝。
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型
     * @return 用户 ID；无法解析时为 {@code null}
     */
    private Long resolveUserId(Object loginId, String loginType) {
        if (loginId == null) {
            log.warn("[auth] 权限查询缺少 loginId，按无权限处理：loginType={}", loginType);
            return null;
        }
        try {
            return Long.valueOf(loginId.toString().trim());
        } catch (NumberFormatException ex) {
            log.warn("[auth] 权限查询的 loginId 不是合法用户 ID，按无权限处理：loginType={}, loginId={}",
                loginType, loginId);
            return null;
        }
    }
}
