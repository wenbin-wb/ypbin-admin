/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.security.core.PermissionProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * system 服务的权限数据源（starter 端口 {@link PermissionProvider} 的宿主实现）。
 *
 * <p><strong>为什么必须有本类</strong>：starter 的注解鉴权把 {@code @SaCheckPermission} 的判定
 * 委托给容器中的 {@link PermissionProvider}；宿主不提供实现时，框架装配的是返回空列表的默认实现
 * （{@code SecurityAutoConfiguration#permissionProvider}），于是所有权限码判定一律不通过。
 * 权限码的唯一数据源是 {@code sys_role_menu} → {@code sys_menu.auth_code}（本仓没有
 * {@code sys_permission} 表），只有本服务能直连该库，故判定语义在 system 侧提供一份，
 * 由 starter 适配成 Sa-Token 的 {@code StpInterface}。</p>
 *
 * <p><strong>超管不掉权限</strong>：本类只做「loginId → userId」的转换与转发，不对结果做任何过滤、
 * 替换或截断。平台超管由 {@link SysPermissionService#listPermissions(Long)} 的实现短路返回
 * {@code *:*:*}（{@code AdminConstants.ALL_PERMISSION}），该通配码是本仓「超管恒有全部权限」的
 * 唯一保障；只要本类保持透传，超管就不会因为「菜单没勾满」或「租户权限模板没包含」而掉权限。</p>
 *
 * <p><strong>失败语义</strong>：loginId 缺失或不是数字时记 WARN 并返回空集合（拒绝，不放行）；
 * 查询本身抛出的异常一律向上抛出，不做捕获兜底（禁静默降级）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Component
@RequiredArgsConstructor
public class SystemPermissionProvider implements PermissionProvider {

    private static final Logger log = LoggerFactory.getLogger(SystemPermissionProvider.class);

    private final SysPermissionService sysPermissionService;

    /**
     * 查询账号的权限码集合。平台超管由底层实现短路返回 {@code *:*:*}，本方法原样透传。
     *
     * @param loginId   登录账号标识（Sa-Token 的 loginId，本仓为 userId 的十进制字符串）
     * @param loginType 账号体系类型
     * @return 权限码列表；loginId 无法解析时为空集合（拒绝）
     */
    @Override
    public List<String> getPermissions(Object loginId, String loginType) {
        Long userId = resolveUserId(loginId, loginType);
        if (userId == null) {
            return List.of();
        }
        return sysPermissionService.listPermissions(userId);
    }

    /**
     * 查询账号的角色码集合。
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型
     * @return 角色码列表；loginId 无法解析时为空集合（拒绝）
     */
    @Override
    public List<String> getRoles(Object loginId, String loginType) {
        Long userId = resolveUserId(loginId, loginType);
        if (userId == null) {
            return List.of();
        }
        return sysPermissionService.listRoleCodes(userId);
    }

    /**
     * 把 Sa-Token 的 loginId 解析为本仓的用户 ID。
     *
     * <p>解析失败时返回 {@code null} 并由调用方转成空集合（拒绝）——这属于「拒绝访问」的显式判定，
     * 不是把故障伪装成正常结果的降级，故记 WARN 留痕而非静默跳过。</p>
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型
     * @return 用户 ID；无法解析时为 {@code null}
     */
    private Long resolveUserId(Object loginId, String loginType) {
        if (loginId == null) {
            log.warn("[system] 权限查询缺少 loginId，按无权限处理：loginType={}", loginType);
            return null;
        }
        try {
            return Long.valueOf(loginId.toString().trim());
        } catch (NumberFormatException ex) {
            log.warn("[system] 权限查询的 loginId 不是合法用户 ID，按无权限处理：loginType={}, loginId={}",
                loginType, loginId);
            return null;
        }
    }
}
