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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 引用翻译数据源测试。
 *
 * <p>覆盖最容易出错的两点：<strong>姓名缺失/空白时回退登录名</strong>，以及
 * <strong>入参 ID 可能是字符串</strong>（框架传入的是字段原值，序列化输出才是字符串）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class DbRefTextProvidersTest {

    private SysUser user(Long id, String realName, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRealName(realName);
        user.setUsername(username);
        return user;
    }

    @Test
    void shouldTranslateUserIdToRealName() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectByIds(any(Collection.class))).thenReturn(List.of(user(1L, "张三", "zhangsan")));
        Map<Object, String> names = new DbRefTextProviders.UserName(mapper).getNames(List.of(1L));
        assertThat(names).containsEntry(1L, "张三");
    }

    @Test
    void shouldFallBackToUsernameWhenRealNameMissing() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectByIds(any(Collection.class))).thenReturn(List.of(
            user(2L, null, "lisi"), user(3L, "  ", "wangwu")));
        Map<Object, String> names = new DbRefTextProviders.UserName(mapper).getNames(List.of(2L, 3L));
        assertThat(names).containsEntry(2L, "lisi").containsEntry(3L, "wangwu");
    }

    @Test
    void shouldAcceptStringIdsAndQueryBatchOnce() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectByIds(any(Collection.class))).thenReturn(List.of(user(9L, "赵六", "zhaoliu")));
        Map<Object, String> names = new DbRefTextProviders.UserName(mapper).getNames(List.of("9"));
        assertThat(names).containsEntry(9L, "赵六");
        // 一次批量查询，不在循环里查库
        verify(mapper).selectByIds(any(Collection.class));
    }

    @Test
    void shouldExposeStarterExpectedType() {
        assertThat(new DbRefTextProviders.UserName(mock(SysUserMapper.class)).type()).isEqualTo("user");
    }
}
