/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.ai.core.AiChatUserContext;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.enums.UserStatusEnum;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 管理端 AI 工具的身份与租户边界测试。
 *
 * <p><b>背景</b>：{@code @Tool} 由 starter-ai 全局注册，知识库公开分享/网页挂件
 * 也会挂载同一批工具。匿名入口只绑定了知识库所属租户，无法据租户区分调用者；
 * 若工具不校验「管理端登录会话」，访客可以借 AI 枚举该租户的用户名/真实姓名。</p>
 *
 * <p>同时锁定租户必须显式传给 system-svc：工具运行在 Reactor 线程，Feign 请求
 * 不携带登录态与租户身份头，服务端无法自行解析租户。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
class AdminAiToolsTest {

    private static final long USER_ID = 9L;
    private static final long TENANT_ID = 100L;

    private final ISystemClient systemFeignClient = mock(ISystemClient.class);
    private final AdminAiTools tools = new AdminAiTools(systemFeignClient);

    @AfterEach
    void clearContext() {
        AiChatUserContext.clear();
        TenantContext.clear();
    }

    @Test
    @DisplayName("公开分享/挂件：无管理端登录上下文时工具拒绝执行，且不调用系统服务")
    void shouldRejectWhenNotAdminSession() {
        assertThat(tools.searchUser("张")).contains("仅在管理端登录会话中可用");
        assertThat(tools.getSystemStats()).contains("仅在管理端登录会话中可用");

        verifyNoInteractions(systemFeignClient);
    }

    @Test
    @DisplayName("管理端会话：按当前租户显式搜索（Feign 线程没有租户身份头）")
    void shouldSearchWithinCurrentTenant() {
        SysUserDto user = new SysUserDto();
        user.setId(1L);
        user.setUsername("zhangsan");
        user.setRealName("张三");
        user.setStatus(UserStatusEnum.ENABLED.getCode());
        when(systemFeignClient.searchUsers("张", TENANT_ID)).thenReturn(R.ok(List.of(user)));

        String result = TenantContext.executeWithTenant(TENANT_ID,
            () -> AiChatUserContext.executeWithUser(USER_ID, () -> tools.searchUser("张")));

        assertThat(result).contains("zhangsan").contains("张三");
        verify(systemFeignClient).searchUsers("张", TENANT_ID);
    }

    @Test
    @DisplayName("缺少租户上下文：给出可见提示，不退化成全局搜索")
    void shouldFailVisiblyWhenTenantMissing() {
        String result = AiChatUserContext.executeWithUser(USER_ID, () -> tools.searchUser("张"));

        assertThat(result).contains("无法确定当前租户");
        verifyNoInteractions(systemFeignClient);
    }

    @Test
    @DisplayName("统计接口同样按租户显式计数")
    void shouldCountWithinCurrentTenant() {
        when(systemFeignClient.countUsers(TENANT_ID)).thenReturn(R.ok(3L));

        String result = TenantContext.executeWithTenant(TENANT_ID,
            () -> AiChatUserContext.executeWithUser(USER_ID, tools::getSystemStats));

        assertThat(result).contains("3");
        verify(systemFeignClient).countUsers(TENANT_ID);
    }
}
