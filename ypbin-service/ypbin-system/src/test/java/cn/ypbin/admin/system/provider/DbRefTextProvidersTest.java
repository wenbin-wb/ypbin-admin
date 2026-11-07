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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引用翻译数据源测试。
 *
 * <p>覆盖最容易出错的三点：<strong>姓名缺失/空白时回退登录名</strong>、
 * <strong>入参 ID 可能是字符串</strong>（框架传入的是字段原值，序列化输出才是字符串），
 * 以及<strong>容错与短路</strong>——脏主键（非数字/空/ null）必须跳过而不是让整批翻译抛异常，
 * 且空主键列表不得进 SQL（CodeQL {@code java/uncaught-number-format-exception} 曾报 3 处）。</p>
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

    @Test
    @DisplayName("parseIds：数字（含带空白的字符串）保留，非数字与 null 跳过")
    void parseIdsShouldKeepParsableAndSkipOthers() {
        Collection<Object> raw = Arrays.asList(1L, "2", " 3 ", "abc", "", null, 4);

        assertThat(DbRefTextProviders.parseIds(raw)).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("parseIds：空输入返回空列表（不抛异常、不返回 null）")
    void parseIdsShouldReturnEmptyForEmptyInput() {
        assertThat(DbRefTextProviders.parseIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("全部引用值非法时：返回空 Map，且**不查库**（空集合不得进 SQL）")
    void allInvalidIdsMustShortCircuitWithoutQuery() {
        SysUserMapper mapper = mock(SysUserMapper.class);

        Map<Object, String> names =
            new DbRefTextProviders.UserName(mapper).getNames(Arrays.asList("abc", "", "null"));

        assertThat(names).isEmpty();
        verify(mapper, never()).selectByIds(any(Collection.class));
    }

    @Test
    @DisplayName("合法与非法混在一起时：只查合法主键，名称按可解析的 key 返回")
    void mixedIdsShouldQueryOnlyParsableOnes() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectByIds(any(Collection.class))).thenReturn(List.of(user(7L, "张三", "u7")));

        Map<Object, String> names = new DbRefTextProviders.UserName(mapper).getNames(Arrays.asList("7", "bad"));

        assertThat(names).containsEntry(7L, "张三");
    }
}
