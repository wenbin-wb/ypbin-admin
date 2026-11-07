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

import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.datapermission.core.AbstractDataScopeHandler;
import cn.ypbin.starter.datapermission.core.DataScopeCalculator;
import cn.ypbin.starter.datapermission.core.DataScopeCalculator.RoleScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 数据范围解析（{@link DataScopeResolver} 实现体）：{@code DataScopeHandler} 端口适配 + 治理表白名单 +
 * 无身份头诊断。算法在 starter 的 {@link AbstractDataScopeHandler}，取数在 {@link DataScopeDataSources}。
 *
 * <p>保留 {@link DataScopeResolver} 接口：写路径（{@code SysUserServiceImpl} 的 deptId 校验）与读路径必须
 * 共用同一口径，且业务层不得依赖 {@code provider} 包。语义细节与迁移前后对照见
 * {@code docs/data-permission.md}。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
public abstract class AbstractDataScopeResolver extends AbstractDataScopeHandler
    implements DataScopeResolver {

    /** 被治理的表（本仓唯一带 {@code dept_id} 且被 {@code @DataPermission} 覆盖的业务表） */
    private static final String TARGET_TABLE = "sys_user";

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final DataScopeDataSources dataSources;

    protected AbstractDataScopeResolver(ObjectProvider<SysRoleMapper> roleMapperProvider,
        ObjectProvider<SysRoleDeptMapper> roleDeptMapperProvider,
        ObjectProvider<SysDeptMapper> deptMapperProvider,
        ObjectProvider<SysPermissionService> permissionServiceProvider) {
        this.dataSources = new DataScopeDataSources(roleMapperProvider, roleDeptMapperProvider,
            deptMapperProvider, permissionServiceProvider);
    }

    /**
     * 读路径入口：白名单 → 无身份头诊断 → 基类算法。
     *
     * <p>必须调 {@code super.getDataScopeSql}：子类 {@code AdminDataScopeHandler} 把
     * {@code getDataScopeSql} 覆写为委托本方法，回调它会无限递归。无身份头时用本仓文案覆盖基类通用文案
     * ——只有本仓知道哪条修复路径真正可行（曾误建议 {@code @DataPermission(ignore = true)}）。</p>
     */
    @Override
    public @Nullable String resolveCondition(String mappedStatementId, String tableName) {
        if (!TARGET_TABLE.equalsIgnoreCase(tableName)) {
            return null; // 白名单必须在取身份/查库之前：非治理表不应触发任何数据范围开销
        }
        if (dataSources.currentUserId().isEmpty()) {
            log.error(DataScopeDiagnostics.NO_IDENTITY_MESSAGE, mappedStatementId, tableName);
            return DataScopeCalculator.denyAllSql(DataScopeCalculator.DEFAULT_ID_COLUMN);
        }
        return super.getDataScopeSql(mappedStatementId, tableName);
    }

    @Override
    protected Optional<Long> currentUserId() {
        return dataSources.currentUserId();
    }

    @Override
    protected @Nullable Long currentUserDeptId() {
        return dataSources.currentUserDeptId();
    }

    @Override
    protected boolean isSuperAdmin(long userId) {
        return dataSources.isSuperAdmin(userId);
    }

    @Override
    protected List<RoleScope> loadRoleScopes(long userId) {
        return dataSources.loadRoleScopes(userId);
    }

    @Override
    protected Map<Long, List<Long>> loadDeptChildren() {
        return dataSources.loadDeptChildren();
    }
}
