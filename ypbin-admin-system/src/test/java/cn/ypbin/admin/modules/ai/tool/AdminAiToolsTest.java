/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.modules.ai.core.AiChatUserContext;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link AdminAiTools} 的「管理端会话准入」测试。
 *
 * <p>公开分享/挂件复用同一批 {@code @Tool}，匿名访客只绑定了知识库所属租户；
 * 若工具不校验管理端会话标记，匿名访客就能借 AI 枚举该租户的用户。</p>
 *
 * @author wenbin
 * @since 2026-11-08
 */
class AdminAiToolsTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);

    private final AdminAiTools tools = new AdminAiTools(userMapper);

    @BeforeAll
    static void initTableInfo() {
        // 断言 wrapper 的 SQL 片段时需要 MyBatis-Plus 的列名缓存（不启动 Spring）
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), "ai-tools"), SysUser.class);
    }

    @AfterEach
    void clearContext() {
        AiChatUserContext.clear();
    }

    @Test
    @DisplayName("无管理端会话标记：所有工具拒绝执行，且不查库")
    void toolsShouldBeDeniedOutsideAdminSession() {
        AiChatUserContext.clear();

        assertThat(tools.searchUser("admin")).contains("仅在管理端登录会话中可用");
        assertThat(tools.getSystemStats()).contains("仅在管理端登录会话中可用");
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("管理端会话：工具正常执行")
    void toolsShouldRunInsideAdminSession() {
        AiChatUserContext.set(1L);
        when(userMapper.selectCount(any())).thenReturn(7L);
        when(userMapper.selectList(any())).thenReturn(List.of());

        assertThat(tools.getSystemStats()).contains("正常用户数: 7");
        assertThat(tools.searchUser("nobody")).contains("未找到匹配用户");
    }

    @Test
    @DisplayName("用户搜索的 OR 条件必须分组，否则租户插件追加的 tenant_id 会被 OR 短路")
    @SuppressWarnings("unchecked")
    void searchUserShouldGroupOrConditionForTenantFilter() {
        AiChatUserContext.set(1L);
        when(userMapper.selectList(any())).thenReturn(List.of());

        tools.searchUser("nobody");

        ArgumentCaptor<Wrapper<SysUser>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // 分组后形如 AND (username LIKE ? OR real_name LIKE ?)；不分组则是 username LIKE ? OR real_name LIKE ?，
        // 租户插件在末尾追加的 AND tenant_id = ? 只能约束到后一个 LIKE 上。
        assertThat(sql).containsIgnoringCase("(username like");
        assertThat(sql).containsIgnoringCase("or real_name like");
    }
}
