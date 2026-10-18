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
import cn.ypbin.starter.security.core.AbstractPermissionProvider;
import cn.ypbin.starter.security.core.PermissionProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * <p><strong>继承 {@link AbstractPermissionProvider} 的理由</strong>：{@code loginId → userId} 的解析
 * 是<b>框架契约</b>而非业务语义（loginId 为 {@code Object}、本仓约定为 userId 十进制字符串），
 * 且「解析失败记 WARN 并返回空集合」「数据源返回 {@code null} 记 ERROR 并按拒绝处理」这套 fail-closed
 * 骨架每个宿主都一样。这段曾在本仓 3 个服务里各抄一份（加 iot 共 4 份），口径一变就要改 4 处，
 * 漏一处的表现是「该服务权限恒空、403」的静默失效。现在统一由 starter 基类承担，
 * 本类只剩两个数据源方法。</p>
 *
 * <p><strong>超管不掉权限</strong>：本类只做「userId → 数据源」的转发，不对结果做任何过滤、
 * 替换或截断。平台超管由 {@link SysPermissionService#listPermissions(Long)} 的实现短路返回
 * {@code *:*:*}（{@code AdminConstants.ALL_PERMISSION}），该通配码是本仓「超管恒有全部权限」的
 * 唯一保障；只要本类保持透传，超管就不会因为「菜单没勾满」或「租户权限模板没包含」而掉权限。</p>
 *
 * <p><strong>失败语义</strong>：loginId 缺失/非法时由基类记 WARN 并返回空集合（拒绝，不放行）；
 * 数据源返回 {@code null} 时由基类记 ERROR 并按空集合拒绝；查询本身抛出的异常一律向上抛出，
 * 不做捕获兜底（禁静默降级）——「服务故障」与「确实无权限」必须可区分。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Component
@RequiredArgsConstructor
public class SystemPermissionProvider extends AbstractPermissionProvider {

    private final SysPermissionService sysPermissionService;

    /**
     * 查询指定用户 ID 的权限码。平台超管由底层实现短路返回 {@code *:*:*}，本方法原样透传。
     *
     * @param userId 用户 ID
     * @return 权限码列表
     */
    @Override
    protected List<String> permissionsOf(long userId) {
        return sysPermissionService.listPermissions(userId);
    }

    /**
     * 查询指定用户 ID 的角色码。
     *
     * @param userId 用户 ID
     * @return 角色码列表
     */
    @Override
    protected List<String> rolesOf(long userId) {
        return sysPermissionService.listRoleCodes(userId);
    }
}
