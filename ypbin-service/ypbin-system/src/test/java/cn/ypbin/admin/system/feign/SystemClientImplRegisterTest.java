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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.admin.system.mapper.SysMessageMapper;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.req.UserRegisterReq;
import cn.ypbin.admin.system.provider.DbLogProviders;
import cn.ypbin.admin.system.service.SocialBindService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysSpaceRelationService;
import cn.ypbin.admin.system.service.SysSpaceService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.social.SocialConfigReader;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.messaging.mail.MailService;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 内部端点 {@code POST /internal/user-register} 的委派测试。
 *
 * <p>与其余用户端点一致：控制器只做路由与视图投影，全部注册校验（开关、租户、角色、查重、密码策略）
 * 落在 {@code SysUserService#registerUser}。本测试钉死「不得在控制器里另写一套注册逻辑」，
 * 也不得返回实体（含密码）。
 *
 * @author wenbin
 * @since 2026-11-10
 */
class SystemClientImplRegisterTest {

    private final SysUserService userService = mock(SysUserService.class);

    private final SystemClientImpl controller = new SystemClientImpl(
        mock(SysPermissionService.class),
        userService,
        mock(SysConfigMapper.class),
        mock(SocialConfigReader.class),
        mock(SocialBindService.class),
        mock(SysMenuService.class),
        mock(SysSpaceService.class),
        mock(SysSpaceRelationService.class),
        mock(SysMessageMapper.class),
        mock(MailService.class),
        mock(ObjectProvider.class),
        new DbLogProviders.DbLogDao(mock(SysLogMapper.class)),
        providerOfTrackRecorder());

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TrackRecorder> providerOfTrackRecorder() {
        ObjectProvider<TrackRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(TrackRecorder.class));
        return provider;
    }

    @Test
    @DisplayName("注册端点：委派 service 并返回不含密码的只读视图")
    void shouldDelegateAndProjectView() {
        UserRegisterReq req = new UserRegisterReq();
        req.setUsername("alice");
        req.setPassword("Passw0rd!");
        req.setRealName("爱丽丝");
        SysUser created = new SysUser();
        created.setId(99L);
        created.setUsername("alice");
        created.setRealName("爱丽丝");
        created.setPassword("$2a$10$secret");
        when(userService.registerUser(req)).thenReturn(created);

        R<SysUserDto> response = controller.registerUser(req);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getId()).isEqualTo(99L);
        assertThat(response.getData().getUsername()).isEqualTo("alice");
        verify(userService).registerUser(req);
    }
}
