/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.ai.entity.AiModelConfig;
import cn.ypbin.admin.ai.mapper.AiModelConfigMapper;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * 向量化模型解析器的租户口径测试。
 *
 * <p><b>修的是什么</b>：{@code resolve()} 曾写 {@code UserContext.getTenantId().orElse(1L)}——无租户上下文时
 * <b>静默</b>用平台默认租户 1。而同模块的对话侧解析器（{@code AiModelConfigResolverImpl}）是
 * 「登录上下文 → {@code TenantContext} → 显式失败」，于是同一份配置在向量化与对话两条链路可能解析到
 * <b>不同租户</b>的模型。这属全局规范的「禁静默降级」红线：配置缺失应显式报错，不能回退魔法值。</p>
 *
 * <p><b>本测试锁定的三条</b>：① 无任何租户上下文 ⇒ 抛 {@code BusinessException} 且<b>不查库</b>
 * （不是静默 1L）；② 匿名链路显式绑定 {@code TenantContext} ⇒ 用它查库；③ 登录上下文优先于
 * {@code TenantContext}，与对话侧顺序一致。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class AiEmbeddingConfigResolverImplTenantTest {

    private static final long LOGIN_TENANT_ID = 7L;

    private static final long BOUND_TENANT_ID = 88L;

    private final AiModelConfigMapper modelConfigMapper = mock(AiModelConfigMapper.class);

    private final AiKeyCipher keyCipher = mock(AiKeyCipher.class);

    private final AiEmbeddingConfigResolverImpl resolver =
        new AiEmbeddingConfigResolverImpl(modelConfigMapper, keyCipher);

    /** MyBatis-Plus 的 Lambda 条件构造器需要实体的 TableInfo 缓存（单测不启动 Spring） */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "embedding-tenant"),
            AiModelConfig.class);
    }

    /** 单测不经过 Spring，避免 ThreadLocal 串用影响其它用例 */
    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("无租户上下文：显式抛业务异常且不查库（不回退平台默认租户 1）")
    void shouldFailExplicitlyWhenNoTenantContext() {
        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.empty());

            assertThatThrownBy(resolver::resolve)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法获取当前租户上下文");
        }
        verifyNoInteractions(modelConfigMapper);
    }

    @Test
    @DisplayName("匿名链路：显式绑定的 TenantContext 租户被采用（不再落到租户 1）")
    void shouldUseExplicitlyBoundTenantContext() {
        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.empty());

            TenantContext.runWithTenant(BOUND_TENANT_ID, () -> assertThat(resolver.resolve()).isNull());
        }

        assertThat(capturedTenantIds()).contains(BOUND_TENANT_ID).doesNotContain(1L);
    }

    @Test
    @DisplayName("登录上下文优先：有登录租户时用登录租户，而不是 TenantContext")
    void shouldPreferLoginContextTenant() {
        try (MockedStatic<UserContext> userContext = Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(LOGIN_TENANT_ID));

            TenantContext.runWithTenant(BOUND_TENANT_ID, () -> assertThat(resolver.resolve()).isNull());
        }

        assertThat(capturedTenantIds()).contains(LOGIN_TENANT_ID).doesNotContain(BOUND_TENANT_ID);
    }

    /**
     * 从查询条件里取出实际用于 {@code tenant_id} 的取值。
     *
     * <p>只取 {@code Long} 类型的参数：该 wrapper 的其它条件（{@code modelType}/{@code isDefault}/
     * {@code status}）都是 String/Integer，{@code tenantId} 是唯一的 {@code Long}——按类型过滤避免
     * 「{@code isDefault=1} 与租户 1 撞值」造成的假断言。</p>
     */
    private List<Long> capturedTenantIds() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AiModelConfig>> captor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(modelConfigMapper).selectOne(captor.capture());
        LambdaQueryWrapper<AiModelConfig> wrapper = captor.getValue();
        // MyBatis-Plus 的条件值是<b>懒填充</b>的：只有生成 SQL 片段时才把参数写进 paramNameValuePairs，
        // 因此必须先调 getSqlSegment()，否则这里永远读到空 map（会让断言恒真/恒假）
        wrapper.getSqlSegment();
        return wrapper.getParamNameValuePairs().values().stream()
            .filter(Long.class::isInstance)
            .map(Long.class::cast)
            .toList();
    }
}
