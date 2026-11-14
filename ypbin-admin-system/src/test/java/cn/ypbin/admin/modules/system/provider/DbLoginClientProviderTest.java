/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.modules.system.entity.SysClient;
import cn.ypbin.admin.modules.system.mapper.SysClientMapper;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.security.client.LoginClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DbLoginClientProvider} 单元测试。
 *
 * <p>后台「客户端管理」里的启停开关与密钥必须真的作用到登录链路：这里钉住状态映射，
 * 避免出现「界面上禁用了、实际还能登录」的假开关。</p>
 *
 * @author wenbin
 * @since 2026-11-14
 */
class DbLoginClientProviderTest {

    private SysClientMapper clientMapper;
    private DbLoginClientProvider provider;

    @BeforeEach
    void setUp() {
        clientMapper = mock(SysClientMapper.class);
        provider = new DbLoginClientProvider(clientMapper);
    }

    private Optional<LoginClient> findBy(SysClient entity) {
        when(clientMapper.selectOne(any())).thenReturn(entity);
        return provider.findByClientId(entity.getClientId());
    }

    private SysClient client(Integer status) {
        SysClient entity = new SysClient();
        entity.setClientId("miniapp");
        entity.setClientType("MINI");
        entity.setAuthTypes("MINIAPP");
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("状态为禁用：映射为 enabled=false，登录会被 starter 拒绝")
    void disabledStatusShouldMapToDisabledClient() {
        LoginClient client = findBy(client(EntityStatus.DISABLED.getCode())).orElseThrow();

        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("状态为启用或为空：按启用处理（与列默认值一致）")
    void enabledOrNullStatusShouldMapToEnabledClient() {
        assertThat(findBy(client(EntityStatus.ENABLED.getCode())).orElseThrow().isEnabled()).isTrue();
        assertThat(findBy(client(null)).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("认证方式与密钥原样映射，不做改名或默认值填充")
    void shouldMapAuthTypesAndSecret() {
        SysClient entity = client(EntityStatus.ENABLED.getCode());
        entity.setClientSecret("s3cret");
        entity.setAuthTypes("MINIAPP,SOCIAL");

        LoginClient client = findBy(entity).orElseThrow();

        assertThat(client.getClientId()).isEqualTo("miniapp");
        assertThat(client.getClientType()).isEqualTo("MINI");
        assertThat(client.getClientSecret()).isEqualTo("s3cret");
        assertThat(client.getAuthTypes()).containsExactly("MINIAPP", "SOCIAL");
    }

    @Test
    @DisplayName("查不到客户端：返回空 Optional，由 starter 报「客户端不存在」")
    void missingClientShouldReturnEmpty() {
        when(clientMapper.selectOne(any())).thenReturn(null);

        assertThat(provider.findByClientId("unknown")).isEmpty();
    }
}
