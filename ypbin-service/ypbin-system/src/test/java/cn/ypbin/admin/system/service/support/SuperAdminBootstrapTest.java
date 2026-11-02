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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 平台超管初始口令引导：只在「仍要求首登改密」时写入部署口令，绝不覆盖用户已改的口令。
 *
 * @author wenbin
 * @since 2026-11-02
 */
class SuperAdminBootstrapTest {

    private static final String INIT_PASSWORD = "Init#Pass123";

    private SysUserMapper userMapper;

    private SuperAdminBootstrap bootstrap;

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 需要实体元数据才能把 SysUser::getPassword 翻译成列名
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "user"),
            SysUser.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        bootstrap = new SuperAdminBootstrap(userMapper);
        ReflectionTestUtils.setField(bootstrap, "initPassword", INIT_PASSWORD);
    }

    @Test
    @DisplayName("★ 仍标记需改密时：用部署口令覆盖种子演示口令（CAS 条件带 pwd_change_required=1）")
    void shouldWriteInitPasswordWhenChangeRequired() {
        SysUser admin = adminWithPassword("$2a$10$seedPlaceholderHash000000000000000000000000000000");
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(userMapper.update(isNull(), any())).thenReturn(1);

        bootstrap.run(mock(ApplicationArguments.class));

        ArgumentCaptor<LambdaUpdateWrapper<SysUser>> captor =
            ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet())
            .as("SET 必须写入 password 与 pwd_reset_time")
            .contains("password").contains("pwd_reset_time");
        assertThat(captor.getValue().getSqlSegment())
            .as("WHERE 必须带 pwd_change_required=1 的 CAS 条件，避免覆盖已改密用户")
            .contains("pwd_change_required");
    }

    @Test
    @DisplayName("★ 已自行改密（标记清零）：重启绝不把口令改回部署值")
    void shouldNotOverwriteChangedPassword() {
        SysUser admin = adminWithPassword("$2a$10$changedByUserHash0000000000000000000000000000000");
        admin.setPwdChangeRequired(0);
        when(userMapper.selectOne(any())).thenReturn(admin);

        bootstrap.run(mock(ApplicationArguments.class));

        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("口令已是部署值：重复启动幂等跳过，不重复写库")
    void shouldSkipWhenAlreadyApplied() {
        SysUser admin = adminWithPassword(PasswordEncoderUtil.encode(INIT_PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(admin);

        bootstrap.run(mock(ApplicationArguments.class));

        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("未注入部署口令（开发/手工部署）：完全不动种子账号")
    void shouldSkipWhenInitPasswordMissing() {
        ReflectionTestUtils.setField(bootstrap, "initPassword", "");

        bootstrap.run(mock(ApplicationArguments.class));

        verifyNoInteractions(userMapper);
    }

    private SysUser adminWithPassword(String password) {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(password);
        admin.setPwdChangeRequired(1);
        return admin;
    }
}
