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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysRoleDept;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * 数据范围迁移（starter #107）的<b>边界语义</b>回归。
 *
 * <p><b>为什么需要它</b>：{@code AbstractDataScopeResolver} 从「377 行自研算法」改为「继承 starter 的
 * {@code AbstractDataScopeHandler}」。这类迁移最危险的不是编译失败，而是<b>某条 fail-closed 语义被悄悄放宽</b>
 * ——例如「部门成环时不再兜底」「多角色从并集变交集」「空 deptId 的写路径从拒绝变放行」。这些都不会报错，
 * 只会静默越权。故本类把 issue #107 验收标准里点名的 5 类边界逐条钉住，与既有的
 * {@code AdminDataScopeHandlerTest}（18 条主路径）互补。</p>
 *
 * <p>本类刻意只用 Mockito 替身 + 真实的 {@code AdminDataScopeHandler}，不启动 Spring。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class DataScopeMigrationBoundaryTest {

    private static final String MAPPED_STATEMENT =
        "cn.ypbin.admin.system.mapper.SysUserMapper.selectList";

    private static final Long USER_ID = 42L;

    private static final Long USER_DEPT_ID = 10L;

    private SysRoleMapper roleMapper;

    private SysRoleDeptMapper roleDeptMapper;

    private SysDeptMapper deptMapper;

    private SysPermissionService permissionService;

    private AdminDataScopeHandler handler;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        roleDeptMapper = mock(SysRoleDeptMapper.class);
        deptMapper = mock(SysDeptMapper.class);
        permissionService = mock(SysPermissionService.class);
        handler = new AdminDataScopeHandler(provider(SysRoleMapper.class, roleMapper),
            provider(SysRoleDeptMapper.class, roleDeptMapper), provider(SysDeptMapper.class, deptMapper),
            provider(SysPermissionService.class, permissionService));
        when(permissionService.isSuperAdmin(any())).thenReturn(false);
        login(USER_DEPT_ID);
    }

    @AfterEach
    void clearIdentity() {
        IdentityContext.clear();
    }

    @Test
    @DisplayName("多角色并集取「最大范围」：DEPT + SELF ⇒ 部门条件 OR 本人条件（绝不是交集）")
    void multiRoleShouldUnionNotIntersect() {
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of(role(1L, 3), role(2L, 4)));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user"))
            .as("若误改成交集，「一个角色管本部门 + 一个角色仅本人」的用户会变成什么都看不到")
            .isEqualTo("(dept_id IN (10) OR id = 42)");
    }

    @Test
    @DisplayName("多角色并集：任一角色为「全部」⇒ 不受限（最大范围覆盖其它角色的收窄）")
    void anyAllRoleShouldMakeUnlimited() {
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of(role(1L, 4), role(2L, 1)));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isNull();
        // 「全部」优先，连角色-部门关联都不该查
        org.mockito.Mockito.verifyNoInteractions(roleDeptMapper);
    }

    @Test
    @DisplayName("部门数据成环：后代展开不死循环、不栈溢出（fail-safe，与迁移前的 visited 兜底一致）")
    void deptCycleShouldNotOverflow() {
        // 10 → 11 → 10 的环（两次编辑即可造出，业务校验只拒绝「父是自己」）
        when(deptMapper.selectList(any())).thenReturn(List.of(dept(10L, 11L), dept(11L, 10L)));
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of(role(1L, 2)));

        String sql = handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user");

        // 迁移前的实现用 visited 集合 + 迭代：环上只收集一次，不会无限递归。
        // starter 的 collectDescendants 同样是「已访问集合 + 显式栈」，语义一致。
        assertThat(sql)
            .as("成环必须正常返回（不抛 StackOverflowError），且只收集一次 11")
            .isEqualTo("dept_id IN (10,11)");
    }

    @Test
    @DisplayName("空 deptId 读路径：部门范围角色但用户无部门 ⇒ id = -1（拒绝全部，不放行）")
    void missingUserDeptShouldDenyAllOnRead() {
        login(null);
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of(role(1L, 2), role(2L, 3)));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("id = -1");
    }

    @Test
    @DisplayName("空 deptId 写路径：目标部门为 null ⇒ 拒绝（读路径 dept_id IN (...) 匹配不到 NULL）")
    void nullTargetDeptShouldBeRejectedOnWrite() {
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of(role(1L, 3)));

        assertThat(handler.isDeptWithinScope(null))
            .as("写进去的用户在部门范围角色下永远读不到，必须拒绝")
            .isFalse();
    }

    @Test
    @DisplayName("空 deptId 读写双语义一致性：「仅本人」角色读路径有 id 条件 ⇒ 写路径必须放行部门无关写入")
    void selfOnlyRoleShouldNotRestrictDeptOnWrite() {
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of(role(1L, 4)));

        // 读路径：仅本人 ⇒ 只有 id 条件，不带部门条件
        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("id = 42");
    }

    @Test
    @DisplayName("超管短路：不查角色、不查部门、返回 null（写路径恒放行）")
    void superAdminShouldShortCircuitBothPaths() {
        when(permissionService.isSuperAdmin(USER_ID)).thenReturn(true);

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isNull();
        assertThat(handler.isDeptWithinScope(999L)).isTrue();
        org.mockito.Mockito.verifyNoInteractions(roleMapper, deptMapper, roleDeptMapper);
    }

    @Test
    @DisplayName("重入防护：解析自身查询再次回调 ⇒ 内层返回 null、外层照常（标记用完即清）")
    void reentrantCallShouldShortCircuitAndRecover() {
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of(role(1L, 3)));
        // isSuperAdmin 内部会 JOIN sys_user ⇒ 拦截器再次回调本处理器
        when(permissionService.isSuperAdmin(USER_ID)).thenAnswer(
            invocation -> handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user") == null);

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isNull();
        // 标记已清理：恢复正常解析
        when(permissionService.isSuperAdmin(USER_ID)).thenReturn(false);
        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("dept_id IN (10)");
    }

    @Test
    @DisplayName("无角色：拒绝全部（fail-closed），且不查部门树与角色-部门关联")
    void noRoleShouldDenyAllWithoutExtraQueries() {
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of());

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("id = -1");
        org.mockito.Mockito.verifyNoInteractions(deptMapper, roleDeptMapper);
    }

    @Test
    @DisplayName("「自定义」多角色：部门并集去重、后代展开，一次批量查询取回")
    void customScopesShouldUnionAndExpandDescendants() {
        when(roleMapper.selectByUserId(USER_ID)).thenReturn(List.of(role(1L, 5), role(2L, 5)));
        when(roleDeptMapper.selectDeptIdsByRoleIds(any())).thenReturn(List.of(
            new SysRoleDept(1L, 30L), new SysRoleDept(1L, 31L), new SysRoleDept(2L, 31L),
            new SysRoleDept(2L, 40L)));
        when(deptMapper.selectList(any())).thenReturn(List.of(
            dept(30L, 0L), dept(31L, 30L), dept(32L, 31L), dept(40L, 0L)));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user"))
            .as("两个自定义角色的部门取并集、去重，并各自向下展开后代")
            .isEqualTo("dept_id IN (30,31,32,40)");
    }

    private void login(Long deptId) {
        LoginUser loginUser = new LoginUser(USER_ID, "tester");
        loginUser.setDeptId(deptId);
        IdentityContext.setLoginUser(loginUser);
    }

    private static SysRole role(Long id, Integer dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setDataScope(dataScope);
        role.setTenantId(10L);
        return role;
    }

    private static SysDept dept(Long id, Long pid) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setPid(pid);
        return dept;
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("target", instance);
        return beanFactory.getBeanProvider(type, false);
    }
}
