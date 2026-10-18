/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysConfig;
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.model.req.ConfigSaveReq;
import cn.ypbin.admin.system.model.req.ConfigUpdateBatchReq;
import cn.ypbin.admin.system.model.resp.ConfigResp;
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 系统参数脱敏与写路径的〈脱敏值不落库〉测试。
 *
 * <p><b>修的是什么</b>：列表页与内部端点对同一份 {@code sys_config} 曾用两套判据与两种形态——
 * 内部端点 6 个后缀 + {@code ****末4位}，列表页 2 个精确键 + 置空 {@code ""}。于是「命中后缀但不属那 2 个
 * 精确键」的配置项在<b>管理面列表页明文回显</b>（internal 却是打码的），这是真实的敏感信息泄露面。
 * 现在两侧统一到 {@code ConfigValueMasker}：本测试锁定列表页这一侧。</p>
 *
 * <p><b>还锁定了写路径</b>：列表页回显脱敏值后，管理员改其它字段会把掩码原样提交回来；若直接写库，
 * 真实密钥会被 {@code ****abcd} 覆盖且不报错（静默损坏）。故 {@code updateConfig}/{@code updateGroup}
 * 对掩码值必须保留库内原值。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class SysConfigServiceImplMaskTest {

    private static final String SENSITIVE_KEY = "DINGTALK_APP_API_KEY";

    private static final String RAW_SECRET = "dingTalkRealSecret0001";

    private static final String MASKED_SECRET = "****0001";

    private SysConfigMapper configMapper;

    private SysConfigServiceImpl configService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "config-mask"), SysConfig.class);
    }

    @BeforeEach
    void setUp() {
        configMapper = mock(SysConfigMapper.class);
        configService = new SysConfigServiceImpl(mock(ApplicationEventPublisher.class));
        // ServiceImpl 的 baseMapper 由容器注入，单测里手工装配（不启动 Spring）
        ReflectionTestUtils.setField(configService, "baseMapper", configMapper);
    }

    @Test
    @DisplayName("列表页：命中后缀判据的键必须打码（含历史上只命中后缀、旧列表页会明文的键）")
    void listByGroupShouldMaskEverySensitiveKey() {
        when(configMapper.selectList(any())).thenReturn(List.of(
            config(SENSITIVE_KEY, RAW_SECRET),
            config("SMS_ACCESS_KEY_SECRET", "LTAI5tRealSecret"),
            config("MAIL_PASSWORD", "smtpAuthCode"),
            config("SITE_NAME", "ypbin-admin")));

        List<ConfigResp> items = configService.listByGroup("sms");

        assertThat(items).hasSize(4);
        assertThat(items).filteredOn(item -> SENSITIVE_KEY.equals(item.getConfigKey()))
            .singleElement()
            .satisfies(item -> assertThat(item.getConfigValue())
                .as("历史泄露面：该键只命中后缀、不属旧列表页的 2 个精确键 ⇒ 旧实现会明文返回")
                .isEqualTo(MASKED_SECRET)
                .isNotEqualTo(RAW_SECRET));
        assertThat(items).filteredOn(item -> "SMS_ACCESS_KEY_SECRET".equals(item.getConfigKey()))
            .singleElement()
            .satisfies(item -> assertThat(item.getConfigValue()).isEqualTo("****cret"));
        assertThat(items).filteredOn(item -> "MAIL_PASSWORD".equals(item.getConfigKey()))
            .singleElement()
            .satisfies(item -> assertThat(item.getConfigValue()).isEqualTo("****Code"));
        // 非敏感键原样返回（不能因为统一脱敏把普通配置也打码）
        assertThat(items).filteredOn(item -> "SITE_NAME".equals(item.getConfigKey()))
            .singleElement()
            .satisfies(item -> assertThat(item.getConfigValue()).isEqualTo("ypbin-admin"));
    }

    @Test
    @DisplayName("写路径：批量保存时提交脱敏占位值 ⇒ 保留库内真实密钥，不把掩码写库")
    void updateGroupShouldKeepStoredSecretWhenMaskedValueSubmitted() {
        SysConfig stored = config(SENSITIVE_KEY, RAW_SECRET);
        stored.setConfigGroup("sms");
        when(configMapper.selectList(any())).thenReturn(List.of(stored));
        when(configMapper.updateById(any(SysConfig.class))).thenReturn(1);

        ConfigUpdateBatchReq req = new ConfigUpdateBatchReq();
        req.setConfigs(Map.of(SENSITIVE_KEY, MASKED_SECRET));

        try (MockedStatic<SysCache> ignored = mockStatic(SysCache.class)) {
            configService.updateGroup("sms", req);
        }

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).updateById(captor.capture());
        assertThat(captor.getValue().getConfigValue())
            .as("把 ****0001 写库会让真实密钥静默损坏且不报错；必须保留库内原值")
            .isEqualTo(RAW_SECRET);
    }

    @Test
    @DisplayName("写路径：批量保存时提交真实新值 ⇒ 正常写入（脱敏保护不能挡住真正的修改）")
    void updateGroupShouldWriteNewValueWhenRealValueSubmitted() {
        SysConfig stored = config(SENSITIVE_KEY, RAW_SECRET);
        stored.setConfigGroup("sms");
        when(configMapper.selectList(any())).thenReturn(List.of(stored));
        when(configMapper.updateById(any(SysConfig.class))).thenReturn(1);

        ConfigUpdateBatchReq req = new ConfigUpdateBatchReq();
        req.setConfigs(Map.of(SENSITIVE_KEY, "brandNewSecret9999"));

        try (MockedStatic<SysCache> ignored = mockStatic(SysCache.class)) {
            configService.updateGroup("sms", req);
        }

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).updateById(captor.capture());
        assertThat(captor.getValue().getConfigValue()).isEqualTo("brandNewSecret9999");
    }

    @Test
    @DisplayName("写路径：新增参数时提交脱敏占位值 ⇒ 显式拒绝（新增没有原值可保留）")
    void createConfigShouldRejectMaskedPlaceholderValue() {
        ConfigSaveReq req = new ConfigSaveReq();
        req.setConfigGroup("sms");
        req.setName("第三方 API Key");
        req.setConfigKey(SENSITIVE_KEY);
        req.setConfigValue(MASKED_SECRET);
        when(configMapper.exists(any())).thenReturn(false);

        assertThatThrownBy(() -> configService.createConfig(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("脱敏后的占位值");
    }

    private static SysConfig config(String configKey, String configValue) {
        SysConfig config = new SysConfig();
        config.setConfigGroup("sms");
        config.setName(configKey);
        config.setConfigKey(configKey);
        config.setConfigValue(configValue);
        config.setBuiltIn(0);
        return config;
    }
}
