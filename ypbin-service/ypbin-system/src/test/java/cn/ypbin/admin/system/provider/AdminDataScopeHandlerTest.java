/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * 数据范围处理器测试。
 *
 * <p>逐条钉死 {@code DataScopeHandler} 端口的返回值契约：非治理表返回 null、平台超管/全部范围不加条件、
 * 各类数据范围拼出的 SQL 片段、以及「取不到身份/无角色/条件为空」时的拒绝全部。所有 Mapper 均以
 * Mockito 替身注入，不做任何真实数据库交互。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class AdminDataScopeHandlerTest {

    private static final String MAPPED_STATEMENT = "cn.ypbin.admin.system.mapper.SysUserMapper.selectList";

    private static final Long CURRENT_USER_ID = 42L;

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
        // 默认：非超管；有部门 10 的用户
        when(permissionService.isSuperAdmin(any())).thenReturn(false);
        login(CURRENT_USER_ID, 10L);
    }

    @AfterEach
    void clearIdentity() {
        IdentityContext.clear();
    }

    @Test
    @DisplayName("非治理表（sys_role/sys_user_role 等）不返回条件，且不触发任何查询")
    void shouldNotScopeTablesOtherThanSysUser() {
        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_role")).isNull();
        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "SYS_USER_ROLE")).isNull();
        verifyNoInteractions(roleMapper, roleDeptMapper, deptMapper, permissionService);
    }

    @Test
    @DisplayName("取不到网关身份头时拒绝全部（不返回 null 放行全量）")
    void shouldDenyAllWhenIdentityMissing() {
        IdentityContext.clear();

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("id = -1");
    }

    /**
     * 缺陷 3 回归：报错给出的「修复指引」必须指向真正可行的机制。
     *
     * <p>原先建议的是 {@code @DataPermission(ignore = true)}，但该机制在「外层已激活数据权限」时
     * <b>不生效</b>（{@code DataPermissionContext} 无挂起语义，切面命中 ignore 只是自己不再 enter），
     * 用户照做后问题依旧。可行的是语句级 {@code @InterceptorIgnore(dataPermission = "true")}。
     * 本用例直接捕获日志文本断言文案，改回旧文案即转红。</p>
     */
    @Test
    @DisplayName("报错指引必须指向语句级 @InterceptorIgnore，不得再教用户用无效的 @DataPermission(ignore = true)")
    void shouldAdviseWorkingMechanismWhenIdentityMissing() {
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(AdminDataScopeHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        try {
            IdentityContext.clear();
            handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user");
        } finally {
            handlerLogger.detachAppender(appender);
        }

        String logged = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));

        assertThat(logged)
            .as("必须给出可行的修复机制")
            .contains("@InterceptorIgnore(dataPermission = \"true\")");
        assertThat(logged)
            .as("不得再建议在该场景无效的 @DataPermission(ignore = true)，并应说明它为何无效")
            .doesNotContain("请在调用侧改用 @DataPermission(ignore = true)")
            .contains("内层 @DataPermission(ignore = true) 不会生效");
    }

    @Test
    @DisplayName("平台超级管理员不受数据范围限制（返回 null 表示不加条件）")
    void shouldNotScopePlatformSuperAdmin() {
        when(permissionService.isSuperAdmin(CURRENT_USER_ID)).thenReturn(true);

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isNull();
        // 超管判定后即返回，不应再查角色
        verify(roleMapper, never()).selectByUserId(any());
    }

    @Test
    @DisplayName("任一角色为「全部数据」时不加条件")
    void shouldNotScopeWhenAnyRoleIsAll() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 3, 10L), role(2L, 1, 10L)));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isNull();
    }

    @Test
    @DisplayName("「本部门及以下」展开部门树后代：dept_id IN (本部门 + 后代)")
    void shouldExpandDeptAndChildScopeWithDescendants() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 2, 10L)));
        givenDeptTree(dept(10L, 0L), dept(11L, 10L), dept(111L, 11L), dept(20L, 0L));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user"))
            .isEqualTo("dept_id IN (10,11,111)");
    }

    @Test
    @DisplayName("「本部门」只含本部门；「仅本人」拼 id 条件")
    void shouldScopeDeptAndSelfSeparately() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 3, 10L)));
        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("dept_id IN (10)");

        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 4, 10L)));
        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("id = 42");
    }

    @Test
    @DisplayName("多角色数据范围取并集：部门条件与本人条件以 OR 组合")
    void shouldMergeMultipleScopesWithOr() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 4, 10L), role(2L, 3, 10L)));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user"))
            .isEqualTo("(dept_id IN (10) OR id = 42)");
    }

    @Test
    @DisplayName("「自定义」用角色-部门关联（批量一次查询）并展开后代")
    void shouldExpandCustomScopeWithBatchQuery() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID))
            .thenReturn(List.of(role(1L, 5, 10L), role(2L, 5, 10L)));
        when(roleDeptMapper.selectDeptIdsByRoleIds(anyList())).thenReturn(List.of(
            new SysRoleDept(1L, 30L), new SysRoleDept(2L, 40L)));
        givenDeptTree(dept(30L, 0L), dept(31L, 30L), dept(40L, 0L));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user"))
            .isEqualTo("dept_id IN (30,31,40)");

        // 批量 IN 一次取回全部自定义角色的部门，禁止逐角色查询（N+1）
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(roleDeptMapper).selectDeptIdsByRoleIds(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("无自定义角色时不发起角色-部门查询（IN 判空短路）")
    void shouldShortCircuitWhenNoCustomRole() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 3, 10L)));

        handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user");

        verify(roleDeptMapper, never()).selectDeptIdsByRoleIds(anyList());
    }

    @Test
    @DisplayName("没有有效角色时拒绝全部")
    void shouldDenyAllWhenNoRole() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of());

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("id = -1");
    }

    @Test
    @DisplayName("部门范围角色但用户没有部门时拒绝全部（不静默放行）")
    void shouldDenyAllWhenDeptScopeButUserHasNoDept() {
        login(CURRENT_USER_ID, null);
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 2, null)));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("id = -1");
    }

    @Test
    @DisplayName("角色数据范围取值脏（未知编码）时不放大范围")
    void shouldIgnoreUnknownDataScope() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 99, 10L)));

        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("id = -1");
    }

    @Test
    @DisplayName("解析自身查询再次回调时短路返回 null，避免无限递归")
    void shouldShortCircuitReentrantCall() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 3, 10L)));
        // 解析过程中（isSuperAdmin 内部会 JOIN sys_user）拦截器会再次回调本类
        when(permissionService.isSuperAdmin(CURRENT_USER_ID))
            .thenAnswer(invocation -> handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user") == null);

        // 外层仍按「本部门」返回正常条件（内层返回 null ⇒ isSuperAdmin 视为 true ⇒ 外层不加条件）
        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isNull();
        // 再次调用时标记已清理，恢复常规解析
        when(permissionService.isSuperAdmin(CURRENT_USER_ID)).thenReturn(false);
        assertThat(handler.getDataScopeSql(MAPPED_STATEMENT, "sys_user")).isEqualTo("dept_id IN (10)");
    }

    @Test
    @DisplayName("写路径：部门范围管理员的可见部门可写、他部门不可写（与读路径 dept_id IN (...) 同口径）")
    void shouldValidateWriteTargetDeptAgainstScope() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 3, 10L)));

        assertThat(handler.isDeptWithinScope(10L))
            .as("本部门在数据范围内，写路径必须放行")
            .isTrue();
        assertThat(handler.isDeptWithinScope(99L))
            .as("他部门不在数据范围内，写路径必须拒绝（否则可把用户建/改到任意部门）")
            .isFalse();
    }

    @Test
    @DisplayName("写路径：平台超级管理员不受数据范围限制")
    void shouldLetPlatformSuperAdminWriteAnyDept() {
        when(permissionService.isSuperAdmin(CURRENT_USER_ID)).thenReturn(true);

        assertThat(handler.isDeptWithinScope(99L)).isTrue();
        // 超管判定后即返回，不应再查角色
        verify(roleMapper, never()).selectByUserId(any());
    }

    @Test
    @DisplayName("写路径：任一角色为「全部数据」时不限部门；「本部门及以下」展开后代部门")
    void shouldExpandDeptTreeForWriteValidation() {
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 2, 10L)));
        givenDeptTree(dept(10L, 0L), dept(11L, 10L), dept(111L, 11L), dept(20L, 0L));

        assertThat(handler.isDeptWithinScope(111L))
            .as("「本部门及以下」必须展开部门树后代，与读路径的 dept_id IN (10,11,111) 一致")
            .isTrue();
        assertThat(handler.isDeptWithinScope(20L)).isFalse();

        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 1, 10L)));
        assertThat(handler.isDeptWithinScope(20L))
            .as("「全部数据」角色不受任何部门限制")
            .isTrue();
    }

    @Test
    @DisplayName("写路径 fail-closed：无身份头 / 无部门 / 无角色时一律拒绝，不放行全量")
    void shouldFailClosedForWriteValidation() {
        // ① 取不到网关身份头
        IdentityContext.clear();
        assertThat(handler.isDeptWithinScope(10L)).isFalse();

        // ② 有身份但没有部门（读路径 dept_id IN (...) 也匹配不到）
        login(CURRENT_USER_ID, null);
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 2, null)));
        assertThat(handler.isDeptWithinScope(10L)).isFalse();

        // ③ 无有效角色
        login(CURRENT_USER_ID, 10L);
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of());
        assertThat(handler.isDeptWithinScope(10L)).isFalse();

        // ④ 目标部门为空（不设部门）⇒ 不在任何部门范围内，拒绝
        when(roleMapper.selectByUserId(CURRENT_USER_ID)).thenReturn(List.of(role(1L, 3, 10L)));
        assertThat(handler.isDeptWithinScope(null))
            .as("deptId 为空时写进去的用户在部门范围角色下永远读不到，必须拒绝")
            .isFalse();
    }

    private void login(Long userId, Long deptId) {
        LoginUser loginUser = new LoginUser(userId, "tester");
        loginUser.setDeptId(deptId);
        IdentityContext.setLoginUser(loginUser);
    }

    private SysRole role(Long id, Integer dataScope, Long tenantId) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setDataScope(dataScope);
        role.setTenantId(tenantId);
        return role;
    }

    private SysDept dept(Long id, Long pid) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setPid(pid);
        return dept;
    }

    private void givenDeptTree(SysDept... depts) {
        when(deptMapper.selectList(any())).thenReturn(List.of(depts));
    }

    /**
     * 用 {@link StaticListableBeanFactory} 造一个只含单个实例的 {@link ObjectProvider}。
     *
     * <p>刻意不用 Mockito 生成 {@code ObjectProvider} 替身：{@code getObject()} 等语义由 Spring 提供，
     * 用真实实现才能与生产装配行为一致。</p>
     *
     * @param type     业务类型
     * @param instance 目标实例
     * @param <T>      类型
     * @return 单实例 Provider
     */
    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("target", instance);
        return beanFactory.getBeanProvider(type, false);
    }
}
