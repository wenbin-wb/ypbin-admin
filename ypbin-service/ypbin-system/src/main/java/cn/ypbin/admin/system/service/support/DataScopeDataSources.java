/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.support;

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysRoleDept;
import cn.ypbin.admin.system.enums.DataScopeEnum;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.datapermission.core.DataScopeCalculator.RoleScope;
import cn.ypbin.starter.datapermission.core.DataScopeCalculator.ScopeType;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 数据范围的<b>数据访问层</b>：把「身份头 + 3 张表」翻译成 starter 算法需要的输入值。
 *
 * <p><b>为什么单独成类</b>：starter #107 上收算法后，宿主侧只剩「约定（白名单/诊断）」与「取数」两件事。
 * 拆开后 <b>两者可以独立演进与独立测试</b>：取数层改查询（例如后续加缓存、换批量语句）不影响适配层；
 * 适配层的白名单/诊断文案变化也不碰 SQL。这不是为凑行数——{@link AbstractDataScopeResolver} 的职责
 * 是「{@code DataScopeResolver} 的端口适配」，本类职责是「Mapper 调用与脏数据留痕」，本就是两件事。</p>
 *
 * <p><b>为什么用 {@link ObjectProvider} 延迟取 Mapper</b>：端口适配器参与构建 MyBatis 拦截器链，
 * 会在 {@code SqlSessionFactory} 初始化早期被创建，直接注入 Mapper 会形成循环依赖。</p>
 *
 * <p><b>脏数据留痕在这里</b>：starter 的算法是纯函数（不写日志），{@code data_scope} 无法识别时必须由
 * 宿主记 WARN，否则会变成「该角色莫名看不到数据」且无任何线索。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@RequiredArgsConstructor
class DataScopeDataSources {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ObjectProvider<SysRoleMapper> roleMapperProvider;
    private final ObjectProvider<SysRoleDeptMapper> roleDeptMapperProvider;
    private final ObjectProvider<SysDeptMapper> deptMapperProvider;
    private final ObjectProvider<SysPermissionService> permissionServiceProvider;

    /** 当前请求用户（网关身份头）。 */
    Optional<Long> currentUserId() {
        return IdentityContext.getUserId();
    }

    /** 当前用户部门（身份头里的 deptId，可能为空）。 */
    @Nullable
    Long currentUserDeptId() {
        return IdentityContext.getLoginUser().map(LoginUser::getDeptId).orElse(null);
    }

    /** 超管判定（本仓权限模型：平台用户 + PLATFORM_SUPER 角色）。 */
    boolean isSuperAdmin(long userId) {
        return permissionServiceProvider.getObject().isSuperAdmin(userId);
    }

    /** 用户全部角色的范围声明；脏 {@code data_scope} 记 WARN 后按 UNKNOWN 收窄（不放大范围）。 */
    List<RoleScope> loadRoleScopes(long userId) {
        List<SysRole> roles = roleMapperProvider.getObject().selectByUserId(userId);
        if (roles.isEmpty()) {
            return List.of();
        }
        List<Long> customRoleIds = roles.stream()
            .filter(role -> DataScopeEnum.CUSTOM.getCode().equals(role.getDataScope()))
            .map(SysRole::getId).toList();
        // 批量取角色-部门关联（循环外一次取回，禁循环内 DB）
        Map<Long, List<Long>> customDeptIds = customRoleIds.isEmpty() ? Map.of()
            : roleDeptMapperProvider.getObject().selectDeptIdsByRoleIds(customRoleIds).stream()
                .collect(Collectors.groupingBy(SysRoleDept::getRoleId, LinkedHashMap::new,
                    Collectors.mapping(SysRoleDept::getDeptId, Collectors.toList())));
        List<RoleScope> scopes = new ArrayList<>(roles.size());
        for (SysRole role : roles) {
            Optional<DataScopeEnum> scope = DataScopeEnum.fromCode(role.getDataScope());
            if (scope.isEmpty()) {
                log.warn("角色数据范围取值无法识别，该角色不贡献任何数据范围：roleId={}, dataScope={}",
                    role.getId(), role.getDataScope());
            }
            scopes.add(new RoleScope(scope.map(DataScopeEnum::scopeType).orElse(ScopeType.UNKNOWN),
                customDeptIds.getOrDefault(role.getId(), List.of())));
        }
        return scopes;
    }

    /** 一次全量查询建「父部门 → 子部门 ID」索引，避免逐层查库。 */
    Map<Long, List<Long>> loadDeptChildren() {
        Map<Long, List<Long>> index = new LinkedHashMap<>();
        for (SysDept dept : deptMapperProvider.getObject().selectList(null)) {
            if (dept.getPid() != null && dept.getId() != null) {
                index.computeIfAbsent(dept.getPid(), key -> new ArrayList<>()).add(dept.getId());
            }
        }
        return index;
    }
}
