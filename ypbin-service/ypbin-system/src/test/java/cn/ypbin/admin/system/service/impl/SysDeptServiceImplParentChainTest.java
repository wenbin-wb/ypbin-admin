/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.model.req.DeptSaveReq;
import cn.ypbin.admin.system.service.SysSpaceRelationService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 部门父链校验测试（递归 CTE 改造回归）。
 *
 * <p><b>背景</b>：{@code validateParent} 原实现把整张 {@code sys_dept} 拉进内存再上溯，
 * 每次新增/修改部门都是一次全表扫描。现改为一次递归 CTE（{@code SysDeptMapper#selectParentChain}）
 * 只取「父 → 根」这一条链。</p>
 *
 * <p><b>本测试锁定什么</b>：改造只换了取数方式，判定语义必须逐条不变——
 * 父不存在、父是自己、父在自己后代下（成环）、层级超限、父链断裂，五种拒绝路径与允许路径各一条。
 * 单测不连 MySQL，故 CTE 返回的父链由 mapper 桩给出，这里验证的是「拿到链之后的判定」。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
class SysDeptServiceImplParentChainTest {

    private static final long TENANT_ID = 100L;
    private static final long PARENT_ID = 100L;
    private static final long SELF_ID = 50L;

    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleDeptMapper roleDeptMapper = mock(SysRoleDeptMapper.class);
    private final SysSpaceRelationService spaceRelationService = mock(SysSpaceRelationService.class);

    private SysDeptServiceImpl deptService;

    @BeforeEach
    void setUp() {
        deptService = new SysDeptServiceImpl(userMapper, roleDeptMapper, spaceRelationService);
        // ServiceImpl 的 baseMapper 由容器注入，单测里手工装配（不启动 Spring）
        ReflectionTestUtils.setField(deptService, "baseMapper", deptMapper);
    }

    @Test
    @DisplayName("父链 9 层：新部门落在第 10 层，允许创建")
    void shouldAllowDeptAtDepthLimit() {
        when(deptMapper.selectParentChain(eq(PARENT_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(chain(9));
        when(deptMapper.insert(any(SysDept.class))).thenReturn(1);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            deptService.createDept(req(PARENT_ID));

            verify(deptMapper).insert(any(SysDept.class));
        }
    }

    @Test
    @DisplayName("父链 10 层：新部门将达第 11 层，拒绝并给出层数")
    void shouldRejectDeptExceedingDepthLimit() {
        when(deptMapper.selectParentChain(eq(PARENT_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(chain(10));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> deptService.createDept(req(PARENT_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门层级不能超过");
            verify(deptMapper, never()).insert(any(SysDept.class));
        }
    }

    @Test
    @DisplayName("CTE 查不到父部门：拒绝（父部门不存在）")
    void shouldRejectMissingParent() {
        when(deptMapper.selectParentChain(eq(PARENT_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(List.of());

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> deptService.createDept(req(PARENT_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("父部门不存在");
        }
    }

    @Test
    @DisplayName("父链链尾不是根：父链断裂（父部门被删/跨租户）时拒绝")
    void shouldRejectBrokenParentChain() {
        when(deptMapper.selectParentChain(eq(PARENT_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(List.of(row(100L, 99L), row(99L, 55L)));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> deptService.createDept(req(PARENT_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("父链断裂");
        }
    }

    @Test
    @DisplayName("父部门就是自己：直接拒绝（不查库）")
    void shouldRejectSelfAsParent() {
        SysDept self = new SysDept();
        self.setId(PARENT_ID);
        self.setPid(0L);
        when(deptMapper.selectById(PARENT_ID)).thenReturn(self);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> deptService.updateDept(PARENT_ID, req(PARENT_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("父部门不能是自己");
            verify(deptMapper, never()).selectParentChain(any(), any(), anyInt());
        }
    }

    @Test
    @DisplayName("父链上出现当前部门：拒绝（部门不能挂到自己的后代下）")
    void shouldRejectCyclicParentChain() {
        when(deptMapper.selectById(SELF_ID)).thenReturn(selfDept());
        when(deptMapper.selectParentChain(eq(PARENT_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(List.of(row(100L, SELF_ID), row(SELF_ID, 0L)));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> deptService.updateDept(SELF_ID, req(PARENT_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("后代");
            verify(deptMapper, never()).updateById(any(SysDept.class));
        }
    }

    @Test
    @DisplayName("拖动深子树：自身未超限但最深后代将超限时拒绝")
    void shouldRejectMovingDeepSubtree() {
        when(deptMapper.selectById(SELF_ID)).thenReturn(selfDept());
        // 父链 4 层 ⇒ 自身落在第 5 层，自身校验通过
        when(deptMapper.selectParentChain(eq(PARENT_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(chain(4));
        // 子树最深相对深度 6 ⇒ 最深后代到第 11 层，必须拒绝
        when(deptMapper.selectSubtreeMaxRelDepth(eq(SELF_ID), eq(TENANT_ID), anyInt()))
            .thenReturn(6);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> deptService.updateDept(SELF_ID, req(PARENT_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过上限");
            verify(deptMapper, never()).updateById(any(SysDept.class));
        }
    }

    /** 构造「父 → 根」父链：第 1 行是候选父，链尾 pid=0。 */
    private static List<Map<String, Object>> chain(int rows) {
        List<Map<String, Object>> chain = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            long id = PARENT_ID - i;
            long pid = i == rows - 1 ? 0L : id - 1;
            chain.add(row(id, pid));
        }
        return chain;
    }

    private static Map<String, Object> row(long id, long pid) {
        return Map.of("id", id, "pid", pid);
    }

    private static SysDept selfDept() {
        SysDept dept = new SysDept();
        dept.setId(SELF_ID);
        dept.setPid(0L);
        return dept;
    }

    private static DeptSaveReq req(Long pid) {
        DeptSaveReq req = new DeptSaveReq();
        req.setPid(pid);
        req.setName("运营部");
        return req;
    }
}
