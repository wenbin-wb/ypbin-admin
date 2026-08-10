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

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.datapermission.core.DataPermissionContext;
import cn.ypbin.starter.datapermission.core.DataScopeHandler;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.UserContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 数据权限范围处理器：按当前用户的角色数据范围计算 SQL 条件片段。
 *
 * <p>本处理器参与构建 MyBatis 内部拦截器链，会在 SqlSessionFactory 初始化早期被创建；若直接注入
 * Mapper 会与 SqlSessionFactory 形成循环依赖。故用 {@link ObjectProvider} 延迟到查询时才解析 Mapper，
 * 打破启动期循环（本处理器仅在请求处理时才真正调用，延迟解析安全）。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
public class AdminDataScopeHandler implements DataScopeHandler {

    private static final int SCOPE_ALL = 1;
    private static final int SCOPE_DEPT_AND_CHILD = 2;
    private static final int SCOPE_DEPT = 3;
    private static final int SCOPE_SELF = 4;
    private static final int SCOPE_CUSTOM = 5;

    private final ObjectProvider<SysRoleMapper> roleMapperProvider;
    private final ObjectProvider<SysRoleDeptMapper> roleDeptMapperProvider;
    private final ObjectProvider<SysDeptMapper> deptMapperProvider;
    private final ObjectProvider<SysPermissionService> permissionServiceProvider;

    public AdminDataScopeHandler(ObjectProvider<SysRoleMapper> roleMapperProvider,
        ObjectProvider<SysRoleDeptMapper> roleDeptMapperProvider,
        ObjectProvider<SysDeptMapper> deptMapperProvider,
        ObjectProvider<SysPermissionService> permissionServiceProvider) {
        this.roleMapperProvider = roleMapperProvider;
        this.roleDeptMapperProvider = roleDeptMapperProvider;
        this.deptMapperProvider = deptMapperProvider;
        this.permissionServiceProvider = permissionServiceProvider;
    }

    private SysRoleMapper roleMapper() {
        return roleMapperProvider.getObject();
    }

    private SysRoleDeptMapper roleDeptMapper() {
        return roleDeptMapperProvider.getObject();
    }

    private SysDeptMapper deptMapper() {
        return deptMapperProvider.getObject();
    }

    private SysPermissionService permissionService() {
        return permissionServiceProvider.getObject();
    }

    @Override
    public String getDataScopeSql(String mappedStatementId, String tableName) {
        if (!"sys_user".equalsIgnoreCase(tableName)) {
            return null;
        }
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            return "id = -1";
        }
        ScopeResult scope;
        DataPermissionContext.exit();
        try {
            if (permissionService().isSuperAdmin(userId)) {
                return null;
            }
            scope = computeScope(userId);
        } finally {
            DataPermissionContext.enter();
        }
        if (scope.all()) {
            return null;
        }
        List<String> conditions = new ArrayList<>();
        if (!scope.deptIds().isEmpty()) {
            conditions.add("dept_id IN (" + scope.deptIds().stream()
                .map(String::valueOf).collect(Collectors.joining(",")) + ")");
        }
        if (scope.self()) {
            conditions.add("id = " + userId);
        }
        return conditions.isEmpty() ? "id = -1" : "(" + String.join(" OR ", conditions) + ")";
    }

    private ScopeResult computeScope(Long userId) {
        List<SysRole> roles = roleMapper().selectByUserId(userId);
        if (roles.isEmpty()) {
            return new ScopeResult(false, false, Set.of());
        }
        if (roles.stream().anyMatch(role -> Integer.valueOf(SCOPE_ALL).equals(role.getDataScope()))) {
            return new ScopeResult(true, false, Set.of());
        }

        Long userDeptId = getCurrentUserDeptId();
        Map<Long, List<SysDept>> pidIndex = buildPidIndex();
        Set<Long> deptIds = new HashSet<>();
        boolean self = false;

        for (SysRole role : roles) {
            int scope = role.getDataScope() == null ? 0 : role.getDataScope();
            switch (scope) {
                case SCOPE_DEPT_AND_CHILD:
                    if (userDeptId != null) {
                        deptIds.add(userDeptId);
                        deptIds.addAll(collectDescendants(userDeptId, pidIndex));
                    }
                    break;
                case SCOPE_DEPT:
                    if (userDeptId != null) {
                        deptIds.add(userDeptId);
                    }
                    break;
                case SCOPE_CUSTOM:
                    List<Long> bindIds = roleDeptMapper().selectDeptIdsByRoleId(role.getId());
                    deptIds.addAll(bindIds);
                    for (Long bindId : bindIds) {
                        deptIds.addAll(collectDescendants(bindId, pidIndex));
                    }
                    break;
                case SCOPE_SELF:
                    self = true;
                    break;
                default:
                    break;
            }
        }
        return new ScopeResult(false, self, deptIds);
    }

    private Long getCurrentUserDeptId() {
        return UserContext.getLoginUser()
            .map(u -> u.getDeptId()).orElse(null);
    }

    private Map<Long, List<SysDept>> buildPidIndex() {
        Map<Long, List<SysDept>> idx = new HashMap<>();
        for (SysDept dept : deptMapper().selectList(null)) {
            idx.computeIfAbsent(dept.getPid(), k -> new ArrayList<>()).add(dept);
        }
        return idx;
    }

    private Set<Long> collectDescendants(Long deptId, Map<Long, List<SysDept>> pidIndex) {
        Set<Long> ids = new HashSet<>();
        for (SysDept child : pidIndex.getOrDefault(deptId, List.of())) {
            ids.add(child.getId());
            ids.addAll(collectDescendants(child.getId(), pidIndex));
        }
        return ids;
    }

    private record ScopeResult(boolean all, boolean self, Set<Long> deptIds) {
    }
}
