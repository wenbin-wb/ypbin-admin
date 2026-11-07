/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.core;

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.starter.security.core.AbstractPermissionProvider;
import cn.ypbin.starter.security.core.PermissionProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ai 服务的权限数据源（starter 端口 {@link PermissionProvider} 的宿主实现）。
 *
 * <p><strong>为什么必须有本类</strong>：starter 在宿主未提供 {@link PermissionProvider} 时装配
 * 返回空列表的默认实现，ai 侧 {@code @SaCheckPermission} 标注的接口会一律判定为无权限
 * （网关放行后仍 403）。ai 无 {@code sys_role_menu}/{@code sys_menu} 表，不能直连共享库
 * （见 {@code deploy/nacos/ypbin-ai.yaml} 无 {@code ypbin.tenant.ignore-tables}，直连会被租户插件
 * 追加 {@code tenant_id} 条件而查不到），故经 {@link SysCache} 复用 system 的唯一一份判定实现。</p>
 *
 * <p><strong>继承 {@link AbstractPermissionProvider} 的理由</strong>：{@code loginId → userId} 的解析
 * 与 fail-closed 骨架是框架契约而非业务语义，曾在本仓 3 个服务里各抄一份（加 iot 共 4 份）。
 * 现在统一由 starter 基类承担，本类只剩两个数据源方法。</p>
 *
 * <p><strong>为什么走 SysCache 而不是每次直接 Feign</strong>：{@code @SaCheckPermission} 在
 * **每个请求**上都要取权限码，直连 Feign 会把鉴权变成每次一次 RPC（且 system 抖动即全站鉴权失败）；
 * {@code SysCache} 命中 Redis 即返回，未命中才回源 system 并回填。缓存**永不过期**，一致性由 system 写
 * 路径的主动失效保证（角色授权/菜单权限码/租户权限模板变更后清 {@code sys:perm:user:*}）；
 * 该失效链路见 {@code SysRoleServiceImpl}/{@code SysMenuServiceImpl}/{@code SysAuthTemplateServiceImpl}。</p>
 *
 * <p><strong>失败语义（fail-closed，禁静默降级）</strong>：<br>
 * ① system 不可达或业务失败：{@code SysCache} 内部经 {@code FeignResponses.dataOrThrow} 抛
 * {@code BusinessException}，本类**不捕获**（基类也不捕获数据源异常），异常向上抛出 ⇒ 鉴权不通过
 * （拒绝）且错误在响应与日志中显式可见，不会被伪装成「无权限 403」或「放行」；<br>
 * ② 响应成功但权限码缺失（null）：基类记 ERROR 后返回空集合（拒绝），不放行也不抛错；<br>
 * ③ loginId 缺失或非法：基类记 WARN 后返回空集合（拒绝），不发起 RPC。</p>
 *
 * <p>本类与 auth 服务的同名实现刻意各留一份：两者是不同的部署单元，需要各自的宿主 Bean；
 * 判定语义只有 system 一份实现，这里只是薄适配，不含任何权限规则。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Component
public class AiPermissionProvider extends AbstractPermissionProvider {

    /**
     * 查询指定用户的权限码（经 {@link SysCache} 复用 system 的判定，缺缓存时回源 Feign 并回填）。
     *
     * @param userId 用户 ID
     * @return 权限码列表
     */
    @Override
    protected List<String> permissionsOf(long userId) {
        return SysCache.getUserPermissions(userId);
    }

    /**
     * 查询指定用户的角色码。
     *
     * @param userId 用户 ID
     * @return 角色码列表
     */
    @Override
    protected List<String> rolesOf(long userId) {
        return SysCache.getUserRoleCodes(userId);
    }
}
