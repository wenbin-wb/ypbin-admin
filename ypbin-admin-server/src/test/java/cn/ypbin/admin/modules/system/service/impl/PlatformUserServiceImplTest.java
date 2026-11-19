/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * 平台账号服务守卫测试。
 *
 * <p>锁住两条安全规则：非平台账号不能被当作平台账号操作；平台账号不能禁用自己。</p>
 *
 * <p>这里用 spy 替换 {@code getOne}，避免为测试启动 Spring 容器与 MyBatis 上下文。</p>
 *
 * @author wenbin
 * @since 2026-11-17
 */
class PlatformUserServiceImplTest {

    private final UserAccountSupport accountSupport = mock(UserAccountSupport.class);

    private final PlatformUserServiceImpl service = spy(new PlatformUserServiceImpl(accountSupport));

    @Test
    @DisplayName("目标不是平台账号时拒绝操作")
    void rejectsNonPlatformUser() {
        doReturn(null).when(service).getOne(any(), anyBoolean());

        assertThatThrownBy(() -> service.updateStatus(99L, 0))
            .isInstanceOf(BusinessException.class)
            .hasMessage("平台账号不存在");
        assertThatThrownBy(() -> service.resetPassword(99L, "Abcd1234"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("平台账号不存在");
    }

    @Test
    @DisplayName("平台账号不能禁用自己")
    void rejectsDisablingSelf() {
        SysUser self = new SysUser();
        self.setId(1L);
        doReturn(self).when(service).getOne(any(), anyBoolean());

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class)) {
            loginHelper.when(LoginHelper::getUserId).thenReturn(1L);

            assertThatThrownBy(() -> service.updateStatus(1L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不允许禁用当前用户");
        }
    }
}
