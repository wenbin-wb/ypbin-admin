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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import cn.ypbin.admin.system.mapper.SysPostMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserSocialMapper;
import cn.ypbin.admin.system.model.req.UserSaveReq;
import cn.ypbin.admin.system.provider.AdminDataScopeHandler;
import cn.ypbin.admin.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.online.OnlineUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 写路径 {@code deptId} 数据范围校验测试（缺陷 5）。
 *
 * <p><b>背景</b>：{@code @DataPermission} 只把范围条件拼进被标注方法内已发出的 SQL，而写方法里的
 * {@code deptId} 是请求入参、不经过查询——{@code createUser} 甚至没有 {@code @DataPermission}。
 * 于是部门范围管理员可以建/改到任意部门。现在两个写方法都先调
 * {@code AdminDataScopeHandler#isDeptWithinScope}（与读路径同一套口径）再落库。</p>
 *
 * <p><b>本测试能证明什么</b>：服务层必须<b>先鉴权再落库</b>——越界时抛出友好业务错误且
 * {@code save}/{@code update} 一次都不能发生；在范围内则继续往下走（用「下一步必然出现的错误」
 * 作为「校验已通过」的证据）。<b>范围判定本身</b>（超管不受限、部门范围内允许、范围外拒绝）
 * 由 {@code AdminDataScopeHandlerTest} 用真实处理器与桩 Mapper 断言，
 * 二者合起来即完整链路；此处不重复实现一遍口径。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
class SysUserServiceImplDeptScopeTest {

    private static final Long TARGET_DEPT_ID = 77L;

    private static final Long USER_ID = 9L;

    private AdminDataScopeHandler dataScopeHandler;

    private UserAccountSupport accountSupport;

    private SysUserServiceImpl userService;

    @BeforeEach
    void setUp() {
        dataScopeHandler = mock(AdminDataScopeHandler.class);
        accountSupport = mock(UserAccountSupport.class);
        userService = spy(new SysUserServiceImpl(
            mock(SysUserRoleMapper.class),
            mock(SysUserPostMapper.class),
            mock(SysUserSocialMapper.class),
            mock(SysRoleMapper.class),
            mock(SysPostMapper.class),
            mock(OnlineUserService.class),
            mock(UserExcelComponent.class),
            accountSupport,
            dataScopeHandler));
    }

    @Test
    @DisplayName("新增：目标部门越界 ⇒ 友好业务错误，且不落库、不做任何查重")
    void createUserShouldRejectDeptOutsideScope() {
        UserSaveReq req = request(TARGET_DEPT_ID);
        whenOutOfScope();

        assertThatThrownBy(() -> userService.createUser(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("目标部门不在你的数据范围内");

        verify(dataScopeHandler).isDeptWithinScope(TARGET_DEPT_ID);
        verify(userService, never()).save(any());
        // 鉴权在查重之前：越权请求不该白跑数据库查重
        verify(accountSupport, never()).checkUsernameUnique(any(), any());
    }

    @Test
    @DisplayName("新增：目标部门在范围内 ⇒ 校验放行，继续走到下一步（缺密码即报下一个错）")
    void createUserShouldPassWhenDeptWithinScope() {
        UserSaveReq req = request(TARGET_DEPT_ID);
        whenInScope();

        assertThatThrownBy(() -> userService.createUser(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("新增用户必须设置密码");

        verify(dataScopeHandler).isDeptWithinScope(TARGET_DEPT_ID);
    }

    @Test
    @DisplayName("编辑：目标部门越界 ⇒ 友好业务错误，且不更新，也不去查目标用户")
    void updateUserShouldRejectDeptOutsideScope() {
        UserSaveReq req = request(TARGET_DEPT_ID);
        whenOutOfScope();

        assertThatThrownBy(() -> userService.updateUser(USER_ID, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("目标部门不在你的数据范围内");

        verify(dataScopeHandler).isDeptWithinScope(TARGET_DEPT_ID);
        verify(userService, never()).update(any(), any());
        verify(userService, never()).getOne(any(), eq(false));
    }

    @Test
    @DisplayName("编辑：目标部门在范围内 ⇒ 校验放行，继续走到取目标用户（取不到即报下一个错）")
    void updateUserShouldPassWhenDeptWithinScope() {
        UserSaveReq req = request(TARGET_DEPT_ID);
        whenInScope();
        doReturn(null).when(userService).getOne(any(), eq(false));

        assertThatThrownBy(() -> userService.updateUser(USER_ID, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("用户不存在或无权操作");

        verify(dataScopeHandler).isDeptWithinScope(TARGET_DEPT_ID);
    }

    private void whenOutOfScope() {
        doReturn(false).when(dataScopeHandler).isDeptWithinScope(any());
    }

    private void whenInScope() {
        doReturn(true).when(dataScopeHandler).isDeptWithinScope(any());
    }

    private UserSaveReq request(Long deptId) {
        UserSaveReq req = new UserSaveReq();
        req.setUsername("newbie");
        req.setRealName("新人");
        req.setDeptId(deptId);
        return req;
    }
}
