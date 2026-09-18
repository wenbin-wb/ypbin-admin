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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.mapper.SysUserPasswordHistoryMapper;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.password.policy.PasswordValidator;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 用户名与手机号全局查重测试。
 *
 * <p><b>背景</b>：{@code SysUserServiceImpl#updateUser} 带 {@code @DataPermission}，其数据范围按部门过滤。
 * 原先的查重直接用内置 {@code exists}，落在该范围内 ⇒ <b>跨部门重名/重号查不到</b> ⇒ 校验通过后由
 * 唯一键抛原始 SQL 错误（显式失败，但提示不友好）。现改为走
 * {@code SysUserMapper#countByUsernameGlobal} / {@code #countByPhoneGlobal}
 * （语句级跳过数据权限拦截器）+ {@code TenantContext.executeIgnore}（关闭租户过滤），实现真正的全局查重。</p>
 *
 * <p><b>本测试能证明什么</b>：单测无 MySQL，无法执行真实 SQL，故「跨部门」以「查重返回的命中行位于
 * 调用者数据范围之外」等价表达——由 mapper 桩返回命中行体现；查重<b>确实脱离了过滤作用域</b>则由两点证明：
 * ① 执行查重时断言 {@code TenantContext.isIgnored()} 为真（租户过滤已关）；②
 * {@code SysUserMapperInterceptorIgnoreTest} 断言这两条语句命中 MyBatis-Plus
 * {@code willIgnoreDataPermission}（数据权限拦截器会跳过它们）。二者合起来即「数据范围之外」。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class UserAccountSupportTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);

    private final UserAccountSupport support = new UserAccountSupport(userMapper,
        mock(SysUserPasswordHistoryMapper.class), mock(SysConfigService.class), mock(PasswordValidator.class));

    @Test
    @DisplayName("同部门重名：抛业务异常并给出友好提示，不得落到数据库唯一键")
    void shouldRejectDuplicatedUsernameInSameDept() {
        when(userMapper.countByUsernameGlobal("tom", null)).thenReturn(1L);

        assertThatThrownBy(() -> support.checkUsernameUnique("tom", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("用户名已存在：tom");
    }

    @Test
    @DisplayName("跨部门重名：同样抛友好业务异常（命中行不在调用者数据范围内也查得到）")
    void shouldRejectDuplicatedUsernameAcrossDepts() {
        // 命中 1 行 = 库里已存在同名用户，而该用户不在当前调用者的部门数据范围内
        when(userMapper.countByUsernameGlobal("cross-dept", null)).thenReturn(1L);

        assertThatThrownBy(() -> support.checkUsernameUnique("cross-dept", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("用户名已存在：cross-dept");
    }

    @Test
    @DisplayName("查重必须在忽略租户过滤的作用域内执行，且退出后恢复（反向证明不越界）")
    void shouldQueryOutsideTenantFilterAndRestoreContext() {
        AtomicBoolean ignoredDuringQuery = new AtomicBoolean(false);
        when(userMapper.countByUsernameGlobal(eq("global"), isNull())).thenAnswer(invocation -> {
            ignoredDuringQuery.set(TenantContext.isIgnored());
            return 0L;
        });

        support.checkUsernameUnique("global", null);

        assertThat(ignoredDuringQuery)
            .as("查重语句执行时租户过滤必须已显式关闭，否则跨租户重名查不到")
            .isTrue();
        assertThat(TenantContext.isIgnored())
            .as("executeIgnore 退出后必须恢复，不能把忽略状态泄漏到后续查询")
            .isFalse();
    }

    @Test
    @DisplayName("编辑场景：排除自身 ID 必须原样下推到查重语句（否则改不动自己）")
    void shouldExcludeSelfOnEdit() {
        when(userMapper.countByUsernameGlobal("tom", 9L)).thenReturn(0L);

        support.checkUsernameUnique("tom", 9L);

        verify(userMapper).countByUsernameGlobal("tom", 9L);
    }

    @Test
    @DisplayName("无重名：不抛异常，也不产生任何写入")
    void shouldPassWhenUsernameNotDuplicated() {
        when(userMapper.countByUsernameGlobal("fresh", null)).thenReturn(0L);

        support.checkUsernameUnique("fresh", null);

        verify(userMapper).countByUsernameGlobal("fresh", null);
        assertThat(TenantContext.isIgnored()).isFalse();
    }

    @Test
    @DisplayName("查重走的是全局语句（不是内置 exists），查重失败不掩盖底层异常")
    void shouldNotSwallowMapperFailure() {
        when(userMapper.countByUsernameGlobal(any(), any()))
            .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> support.checkUsernameUnique("tom", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("db down");
    }

    @Test
    @DisplayName("手机号同部门重号：抛业务异常并给出友好提示，不得落到数据库唯一键")
    void shouldRejectDuplicatedPhoneInSameDept() {
        when(userMapper.countByPhoneGlobal("13800000001", null)).thenReturn(1L);

        assertThatThrownBy(() -> support.checkPhoneUnique("13800000001", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("手机号已存在：13800000001");
    }

    @Test
    @DisplayName("手机号跨部门重号：同样抛友好业务异常（命中行不在调用者数据范围内也查得到）")
    void shouldRejectDuplicatedPhoneAcrossDepts() {
        // 命中 1 行 = 库里已存在同号用户，而该用户不在当前调用者的部门数据范围内
        when(userMapper.countByPhoneGlobal("cross-dept-phone", null)).thenReturn(1L);

        assertThatThrownBy(() -> support.checkPhoneUnique("cross-dept-phone", null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("手机号已存在：cross-dept-phone");
    }

    @Test
    @DisplayName("手机号查重必须在忽略租户过滤的作用域内执行，且退出后恢复（反向证明不越界）")
    void shouldQueryPhoneOutsideTenantFilterAndRestoreContext() {
        AtomicBoolean ignoredDuringQuery = new AtomicBoolean(false);
        when(userMapper.countByPhoneGlobal(eq("13800000002"), isNull())).thenAnswer(invocation -> {
            ignoredDuringQuery.set(TenantContext.isIgnored());
            return 0L;
        });

        support.checkPhoneUnique("13800000002", null);

        assertThat(ignoredDuringQuery)
            .as("手机号查重时租户过滤必须已显式关闭，否则跨租户重号查不到")
            .isTrue();
        assertThat(TenantContext.isIgnored())
            .as("executeIgnore 退出后必须恢复，不能把忽略状态泄漏到后续查询")
            .isFalse();
    }

    @Test
    @DisplayName("手机号编辑场景：排除自身 ID 必须原样下推到查重语句（否则改不动自己）")
    void shouldExcludeSelfOnPhoneEdit() {
        when(userMapper.countByPhoneGlobal("13800000003", 9L)).thenReturn(0L);

        support.checkPhoneUnique("13800000003", 9L);

        verify(userMapper).countByPhoneGlobal("13800000003", 9L);
    }

    @Test
    @DisplayName("手机号为空时不查库（未填手机号是合法输入，不产生任何查询）")
    void shouldSkipPhoneCheckWhenPhoneIsNull() {
        support.checkPhoneUnique(null, null);

        verify(userMapper, never()).countByPhoneGlobal(any(), any());
    }

    @Test
    @DisplayName("手机号查重失败不掩盖底层异常（不把 DB 故障伪装成「无重号」）")
    void shouldNotSwallowPhoneMapperFailure() {
        when(userMapper.countByPhoneGlobal(any(), any()))
            .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> support.checkPhoneUnique("13800000004", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("db down");
    }
}
