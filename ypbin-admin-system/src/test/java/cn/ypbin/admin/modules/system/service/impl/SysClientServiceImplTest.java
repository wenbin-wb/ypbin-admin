/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.modules.system.entity.SysClient;
import cn.ypbin.admin.modules.system.mapper.SysClientMapper;
import cn.ypbin.admin.modules.system.model.req.SysClientSaveReq;
import cn.ypbin.admin.modules.system.model.resp.ClientResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.autoconfigure.SecurityProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link SysClientServiceImpl} 单元测试（mock Mapper，不连数据库）。
 *
 * <p>钉住三件事：新建客户端默认不带密钥（否则浏览器/小程序端无法登录）、
 * 密钥可以被清除、启停状态可以被修改。三者都直接决定某个终端「能不能登录」。</p>
 *
 * @author wenbin
 * @since 2026-11-14
 */
class SysClientServiceImplTest {

    private SysClientMapper clientMapper;
    private SysClientServiceImpl service;

    /**
     * Lambda 条件构造器需要实体的 TableInfo 缓存；正常由 MyBatis 启动时初始化，
     * 纯单测里手动注册一次，否则 {@code LambdaUpdateWrapper.set(...)} 会抛“找不到 lambda cache”。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysClient.class);
    }

    @BeforeEach
    void setUp() {
        clientMapper = mock(SysClientMapper.class);
        // SecurityProperties 的默认客户端是 web-admin
        service = new SysClientServiceImpl(new SecurityProperties());
        ReflectionTestUtils.setField(service, "baseMapper", clientMapper);
    }

    private SysClientSaveReq req(String clientId) {
        SysClientSaveReq req = new SysClientSaveReq();
        req.setClientId(clientId);
        req.setClientType("MINI");
        req.setAuthTypes("MINIAPP");
        return req;
    }

    @Test
    @DisplayName("新建客户端不生成密钥：无密钥即可登录，返回体标记未配置密钥")
    void createClientShouldNotGenerateSecret() {
        when(clientMapper.exists(any())).thenReturn(false);
        when(clientMapper.insert(any(SysClient.class))).thenReturn(1);

        ClientResp resp = service.createClient(req("app-a"));

        assertThat(resp.getClientId()).isEqualTo("app-a");
        assertThat(resp.getSecretConfigured()).isFalse();
        ArgumentCaptor<SysClient> captor = ArgumentCaptor.forClass(SysClient.class);
        verify(clientMapper).insert(captor.capture());
        assertThat(captor.getValue().getClientSecret()).isNull();
    }

    @Test
    @DisplayName("清除密钥：只更新 client_secret 列，不动 status")
    @SuppressWarnings("unchecked")
    void clearSecretShouldSetSecretToNull() {
        when(clientMapper.selectById(1L)).thenReturn(existingClient());
        when(clientMapper.update(isNull(), any())).thenReturn(1);

        service.clearSecret(1L);

        ArgumentCaptor<Wrapper<SysClient>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(clientMapper).update(isNull(), captor.capture());
        String sqlSet = ((AbstractWrapper<?, ?, ?>) captor.getValue()).getSqlSet();
        assertThat(sqlSet).contains("client_secret");
        assertThat(sqlSet).doesNotContain("status");
    }

    @Test
    @DisplayName("修改状态：只更新 status 列，不动密钥")
    @SuppressWarnings("unchecked")
    void updateStatusShouldSetStatusOnly() {
        when(clientMapper.selectById(1L)).thenReturn(existingClient());
        when(clientMapper.update(isNull(), any())).thenReturn(1);

        service.updateStatus(1L, 0);

        ArgumentCaptor<Wrapper<SysClient>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(clientMapper).update(isNull(), captor.capture());
        String sqlSet = ((AbstractWrapper<?, ?, ?>) captor.getValue()).getSqlSet();
        assertThat(sqlSet).contains("status");
        assertThat(sqlSet).doesNotContain("client_secret");
    }

    @Test
    @DisplayName("客户端不存在：清除密钥与改状态都显式报错，不静默成功")
    void missingClientShouldThrow() {
        when(clientMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.clearSecret(404L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("客户端不存在");
        assertThatThrownBy(() -> service.updateStatus(404L, 0))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("客户端不存在");
    }

    @Test
    @DisplayName("列表：只返回是否配置密钥的标志，不返回密钥本身")
    void listShouldExposeSecretFlagOnly() {
        SysClient withSecret = existingClient();
        withSecret.setClientSecret("s3cret");
        SysClient withoutSecret = existingClient();
        when(clientMapper.selectList(any())).thenReturn(List.of(withSecret, withoutSecret));

        assertThat(service.listClients())
            .extracting(ClientResp::getSecretConfigured)
            .containsExactly(true, false);
    }

    @Test
    @DisplayName("默认客户端受保护：不允许停用、删除、配置密钥或改 clientId")
    void defaultClientShouldBeProtected() {
        SysClient defaultClient = existingClient();
        defaultClient.setClientId("web-admin");
        when(clientMapper.selectById(1L)).thenReturn(defaultClient);

        assertThatThrownBy(() -> service.updateStatus(1L, 0))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("默认客户端不允许停用");
        assertThatThrownBy(() -> service.deleteClient(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("默认客户端不允许删除");
        assertThatThrownBy(() -> service.resetSecret(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("默认客户端不允许配置密钥");

        // 改 clientId 会让「未声明身份的登录」找不到客户端，同样必须拒绝
        when(clientMapper.exists(any())).thenReturn(false);
        SysClientSaveReq req = req("web-admin-renamed");
        assertThatThrownBy(() -> service.updateClient(1L, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("默认客户端不允许修改客户端 ID");
    }

    private SysClient existingClient() {
        SysClient client = new SysClient();
        client.setId(1L);
        client.setClientId("miniapp");
        client.setClientType("MINI");
        return client;
    }
}
