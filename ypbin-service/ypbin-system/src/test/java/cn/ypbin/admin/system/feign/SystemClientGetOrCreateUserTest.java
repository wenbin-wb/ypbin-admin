/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.service.SocialBindService;
import cn.ypbin.admin.system.social.SocialConfigReader;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 「按用户名查询或创建用户」通用端点的行为回归用例。
 *
 * <p>为什么必须有它：这段逻辑此前**零测试覆盖**，而它在一次「通用化重构」中改变过落库语义
 * （首次登录未带昵称时 {@code real_name} 从 {@code 微信用户} 变成 {@code wx_<openid>}），
 * 是靠独立复核读代码才发现的。三条不变量必须被钉住：</p>
 * <ol>
 *   <li>新建 + 无昵称 → 用调用方给的<b>端侧默认展示名</b>（端侧语义留在端侧）；</li>
 *   <li>新建 + 无昵称 + 未给默认名 → 回退 {@code username}；</li>
 *   <li><b>已存在用户不会被默认展示名改名</b>（只有显式传了非空昵称才更新）。</li>
 * </ol>
 *
 * @author wenbin
 * @since 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
class SystemClientGetOrCreateUserTest {

    @Mock
    private SysPermissionService permissionService;

    @Mock
    private SysUserService userService;

    @Mock
    private SysConfigMapper configMapper;

    @Mock
    private SocialConfigReader socialConfigReader;

    @Mock
    private SocialBindService socialBindService;

    @Mock
    private SysMenuService menuService;

    @Mock
    private LogDao logDao;

    @Mock
    private ObjectProvider<TrackRecorder> trackRecorderProvider;

    @InjectMocks
    private SystemClientImpl systemClient;

    private SysUser createdUser;

    private void givenNoExistingUser() {
        when(userService.getOne(any())).thenReturn(null);
        when(userService.save(any(SysUser.class))).thenAnswer(invocation -> {
            createdUser = invocation.getArgument(0);
            return true;
        });
    }

    @Test
    @DisplayName("新建且未带昵称 → 使用调用方给的端侧默认展示名（如小程序传「微信用户」）")
    void createWithoutNicknameShouldUseCallerDefaultRealName() {
        givenNoExistingUser();

        SysUserDto dto = systemClient
            .getOrCreateUserByUsername("wx_openid_1", null, null, "MINIAPP", "微信用户")
            .getData();

        assertThat(createdUser.getRealName()).isEqualTo("微信用户");
        assertThat(createdUser.getUserType()).isEqualTo("MINIAPP");
        // 昵称只在显式传入时才有值；端侧展示层按「昵称 → realName」回退（MiniappCommonController 即如此）
        assertThat(createdUser.getNickname()).isNull();
        assertThat(dto.getRealName()).isEqualTo("微信用户");
    }

    @Test
    @DisplayName("新建且带了昵称 → 用昵称（端侧默认名不生效）")
    void createWithNicknameShouldPreferNickname() {
        givenNoExistingUser();

        systemClient.getOrCreateUserByUsername("wx_openid_2", "小张", null, "MINIAPP", "微信用户");

        assertThat(createdUser.getRealName()).isEqualTo("小张");
        assertThat(createdUser.getNickname()).isEqualTo("小张");
    }

    @Test
    @DisplayName("新建、无昵称且调用方未给默认名 → 回退 username（通用端点的兜底）")
    void createWithoutNicknameAndDefaultShouldFallbackToUsername() {
        givenNoExistingUser();

        systemClient.getOrCreateUserByUsername("wx_openid_3", null, null, "TENANT", null);

        assertThat(createdUser.getRealName()).isEqualTo("wx_openid_3");
    }

    @Test
    @DisplayName("已存在用户且请求未带昵称 → 不得改名、不得落库更新（默认名只影响新建）")
    void existingUserMustNotBeRenamedByDefaultRealName() {
        SysUser existing = new SysUser();
        existing.setId(9L);
        existing.setUsername("wx_openid_4");
        existing.setNickname("张三");
        existing.setRealName("张三");
        when(userService.getOne(any())).thenReturn(existing);

        SysUserDto dto = systemClient
            .getOrCreateUserByUsername("wx_openid_4", null, null, "MINIAPP", "微信用户")
            .getData();

        assertThat(existing.getRealName()).isEqualTo("张三");
        assertThat(existing.getNickname()).isEqualTo("张三");
        assertThat(dto.getNickname()).isEqualTo("张三");
        verify(userService, never()).updateById(any(SysUser.class));
        verify(userService, never()).save(any(SysUser.class));
    }

    @Test
    @DisplayName("已存在用户且带了新昵称 → 昵称与展示名一起更新")
    void existingUserShouldBeRenamedWhenNicknameProvided() {
        SysUser existing = new SysUser();
        existing.setId(10L);
        existing.setUsername("wx_openid_5");
        existing.setNickname("旧名");
        existing.setRealName("旧名");
        when(userService.getOne(any())).thenReturn(existing);

        systemClient.getOrCreateUserByUsername("wx_openid_5", "新名", null, "MINIAPP", "微信用户");

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateById(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("新名");
        assertThat(captor.getValue().getRealName()).isEqualTo("新名");
    }
}
