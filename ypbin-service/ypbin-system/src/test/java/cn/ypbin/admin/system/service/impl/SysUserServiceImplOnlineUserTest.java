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
package cn.ypbin.admin.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysPostMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserSocialMapper;
import cn.ypbin.admin.system.model.query.OnlineUserQuery;
import cn.ypbin.admin.system.model.resp.OnlineUserResp;
import cn.ypbin.admin.system.provider.AdminDataScopeHandler;
import cn.ypbin.admin.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.security.online.OnlineUser;
import cn.ypbin.starter.security.online.OnlineUserService;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 在线用户分页测试。
 *
 * <p>在线用户来自会话存储，分页只能在服务内切片，故这里钉住四件事：① 页码/每页条数切片正确；
 * ② 越界页返回空列表但 {@code total} 仍是真实总数（前端分页器依赖 total 计算页数）；
 * ③ 关键字过滤沿用会话枚举接口的语义（有值传参、无值不带参）；④ 补充真实姓名走批量 IN，
 * 且在线用户为空时不发起该查询。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class SysUserServiceImplOnlineUserTest {

    /** 与他租户用户区分用的租户 ID（断言姓名回填不受当前租户限制） */
    private static final Long OTHER_TENANT_ID = 999L;

    private OnlineUserService onlineUserService;

    private SysUserServiceImpl userService;

    @BeforeEach
    void setUp() {
        onlineUserService = mock(OnlineUserService.class);
        userService = spy(new SysUserServiceImpl(
            mock(SysUserRoleMapper.class),
            mock(SysUserPostMapper.class),
            mock(SysUserSocialMapper.class),
            mock(SysRoleMapper.class),
            mock(SysPostMapper.class),
            onlineUserService,
            mock(UserExcelComponent.class),
            mock(UserAccountSupport.class),
            mock(AdminDataScopeHandler.class)));
        // 真实姓名补充来自基类 listByIds（批量 IN），测试里替换为可控数据
        doReturn(List.of()).when(userService).listByIds(anyList());
    }

    @Test
    @DisplayName("第一页：按页码/每页条数切片，total 为全量在线数")
    void shouldSliceFirstPage() {
        when(onlineUserService.list()).thenReturn(List.of(online(1L, "u1"), online(2L, "u2"), online(3L, "u3")));

        PageResult<OnlineUserResp> result = userService.pageOnlineUsers(query(1, 2, null));

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(2);
        assertThat(result.getItems()).extracting(OnlineUserResp::getUsername).containsExactly("u1", "u2");
    }

    @Test
    @DisplayName("第二页：返回剩余记录，真实姓名按 userId 批量补充")
    void shouldSliceSecondPageAndFillRealName() {
        when(onlineUserService.list()).thenReturn(List.of(online(1L, "u1"), online(2L, "u2"), online(3L, "u3")));
        doReturn(List.of(user(3L, "张三"))).when(userService).listByIds(List.of(3L));

        PageResult<OnlineUserResp> result = userService.pageOnlineUsers(query(2, 2, null));

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getUsername()).isEqualTo("u3");
        assertThat(result.getItems().get(0).getRealName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("越界页：items 为空但 total 保持真实总数（前端分页器据此计算页数）")
    void shouldReturnEmptyItemsForOutOfRangePage() {
        when(onlineUserService.list()).thenReturn(List.of(online(1L, "u1"), online(2L, "u2")));

        PageResult<OnlineUserResp> result = userService.pageOnlineUsers(query(5, 10, null));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getPages()).isEqualTo(1);
        // 越界页不应再为用户姓名发起查询
        verify(userService, never()).listByIds(anyList());
    }

    @Test
    @DisplayName("关键字：有值时透传给会话枚举，无值时不带参")
    void shouldKeepKeywordSemantics() {
        when(onlineUserService.list("tester")).thenReturn(List.of(online(1L, "tester")));
        when(onlineUserService.list()).thenReturn(List.of());

        assertThat(userService.pageOnlineUsers(query(1, 10, "tester")).getTotal()).isEqualTo(1);
        verify(onlineUserService).list("tester");

        assertThat(userService.pageOnlineUsers(query(1, 10, "  ")).getTotal()).isZero();
        verify(onlineUserService).list();
    }

    @Test
    @DisplayName("在线用户为空时不发起姓名批量查询（IN 判空短路）")
    void shouldShortCircuitBatchQueryWhenNoOnlineUser() {
        when(onlineUserService.list()).thenReturn(List.of());

        PageResult<OnlineUserResp> result = userService.pageOnlineUsers(query(1, 10, null));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(userService, never()).listByIds(anyList());
    }

    @Test
    @DisplayName("非法分页参数显式报错，不做静默纠正")
    void shouldRejectInvalidPagingArguments() {
        assertThatThrownBy(() -> userService.pageOnlineUsers(query(0, 10, null)))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> userService.pageOnlineUsers(query(1, 0, null)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("他租户的在线用户同样要回填姓名（会话跨租户 + @PlatformAccess 平台级语义 ⇒ 回填必须全局）")
    void shouldFillRealNameForOnlineUserFromAnotherTenant() {
        OnlineUser otherTenantUser = online(9L, "other-tenant-user");
        otherTenantUser.setTenantId(OTHER_TENANT_ID);
        when(onlineUserService.list()).thenReturn(List.of(otherTenantUser));
        AtomicBoolean ignoredDuringLookup = new AtomicBoolean(false);
        doAnswer(invocation -> {
            ignoredDuringLookup.set(TenantContext.isIgnored());
            return List.of(user(9L, "李四"));
        }).when(userService).listByIds(List.of(9L));

        PageResult<OnlineUserResp> result = userService.pageOnlineUsers(query(1, 10, null));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getUsername()).isEqualTo("other-tenant-user");
        assertThat(result.getItems().get(0).getRealName())
            .as("他租户用户的姓名必须同样回填，否则平台管理员看到的在线列表里这部分姓名恒为空")
            .isEqualTo("李四");
        assertThat(ignoredDuringLookup)
            .as("姓名批量查询必须脱离租户过滤（sys_user 不在 ignore-tables，否则他租户行被 tenant_id 条件滤掉）")
            .isTrue();
        assertThat(TenantContext.isIgnored())
            .as("executeIgnore 退出后必须恢复，不能把忽略状态泄漏给同一请求的后续查询")
            .isFalse();
    }

    @Test
    @DisplayName("反向证明：本租户回填同样走忽略作用域，且查询失败不吞异常")
    void shouldNotSwallowBackfillFailure() {
        when(onlineUserService.list()).thenReturn(List.of(online(1L, "u1")));
        doAnswer(invocation -> {
            throw new IllegalStateException("db down");
        }).when(userService).listByIds(anyList());

        assertThatThrownBy(() -> userService.pageOnlineUsers(query(1, 10, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("db down");
        assertThat(TenantContext.isIgnored())
            .as("异常路径也必须退出忽略作用域")
            .isFalse();
    }

    private OnlineUserQuery query(long page, long pageSize, String keyword) {
        OnlineUserQuery query = new OnlineUserQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        return query;
    }

    private OnlineUser online(Long userId, String username) {
        OnlineUser user = new OnlineUser();
        user.setUserId(userId);
        user.setUsername(username);
        return user;
    }

    private SysUser user(Long id, String realName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRealName(realName);
        return user;
    }
}
