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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysConfig;
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.provider.DbLogProviders;
import cn.ypbin.admin.system.service.SocialBindService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysSpaceRelationService;
import cn.ypbin.admin.system.mapper.SysMessageMapper;
import cn.ypbin.starter.messaging.mail.MailService;
import cn.ypbin.admin.system.service.SysSpaceService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.social.SocialConfigReader;
import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 内部端点 {@code GET /internal/config-by-key} 的脱敏契约测试。
 *
 * <p><b>为什么需要它</b>：本端点与列表页曾各自实现一套脱敏（判据集合与返回形态都不同），导致
 * 「internal 打码、列表页明文」的泄露面、同键跨入口形态不一致。现两侧统一到
 * {@code ConfigValueMasker}，本测试锁定 internal 这一侧的输出形态，并显式断言非敏感键不被误脱敏。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class SystemClientImplConfigMaskTest {

    /** 掩码前缀（与 ConfigValueMasker 的形态一致） */
    private static final String MASK_PREFIX = "****";

    /** 验收者实测「internal 明文」的 6 个缺口键 */
    private static final List<String> GAP_KEYS = List.of(
        "ALIYUN_SECRET_KEY", "MY_SECRET_KEY", "DB_PASSWD", "SERVICE_PWD",
        "THIRD_PARTY_CREDENTIAL", "SOCIAL_ALIPAY_PUBLIC_KEY");

    private final SysConfigMapper configMapper = mock(SysConfigMapper.class);

    private final SystemClientImpl controller = new SystemClientImpl(
        mock(SysPermissionService.class),
        mock(SysUserService.class),
        configMapper,
        mock(SocialConfigReader.class),
        mock(SocialBindService.class),
        mock(SysMenuService.class),
        mock(SysSpaceService.class),
        mock(SysSpaceRelationService.class),
        mock(SysMessageMapper.class),
        mock(MailService.class),
        new DbLogProviders.DbLogDao(mock(SysLogMapper.class)),
        providerOfTrackRecorder());

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TrackRecorder> providerOfTrackRecorder() {
        ObjectProvider<TrackRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(TrackRecorder.class));
        return provider;
    }

    @Test
    @DisplayName("敏感键：返回值只保留末 4 位，明文不出 internal 端点")
    void sensitiveKeyShouldBeMasked() {
        when(configMapper.selectOne(any(), any(Boolean.class)))
            .thenReturn(config("SMS_ACCESS_KEY_SECRET", "LTAI5tRealSecret"));

        R<ConfigValue> response = controller.getConfigByKey("SMS_ACCESS_KEY_SECRET");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getConfigValue())
            .isEqualTo("****cret")
            .doesNotContain("LTAI5tRealSecret");
    }

    @Test
    @DisplayName("后缀命中即敏感：不属旧精确键清单的键同样打码（历史泄露面的另一侧）")
    void suffixMatchedKeyShouldAlsoBeMasked() {
        when(configMapper.selectOne(any(), any(Boolean.class)))
            .thenReturn(config("DINGTALK_APP_API_KEY", "dingTalkRealSecret0001"));

        R<ConfigValue> response = controller.getConfigByKey("DINGTALK_APP_API_KEY");

        assertThat(response.getData().getConfigValue()).isEqualTo("****0001");
    }

    @Test
    @DisplayName("缺口键：验收者实测为「internal 明文」的 6 个键必须打成 ****末4位")
    void previouslyGapKeysShouldBeMasked() {
        for (String configKey : GAP_KEYS) {
            when(configMapper.selectOne(any(), any(Boolean.class)))
                .thenReturn(config(configKey, "secretValue" + configKey.length()));

            R<ConfigValue> response = controller.getConfigByKey(configKey);

            assertThat(response.getData().getConfigValue())
                .as("缺口键 %s 经 internal 必须打码（旧判据下是明文）", configKey)
                .startsWith(MASK_PREFIX)
                .isNotEqualTo("secretValue" + configKey.length());
        }
    }

    @Test
    @DisplayName("标识符键 SMS_ACCESS_KEY_ID：保持明文（单独不能通过认证，打码妨碍运维确认）")
    void accessKeyIdShouldStayPlain() {
        when(configMapper.selectOne(any(), any(Boolean.class)))
            .thenReturn(config("SMS_ACCESS_KEY_ID", "LTAI5tAccessKeyId"));

        R<ConfigValue> response = controller.getConfigByKey("SMS_ACCESS_KEY_ID");

        assertThat(response.getData().getConfigValue()).isEqualTo("LTAI5tAccessKeyId");
    }

    @Test
    @DisplayName("非敏感键：原文返回（登录开关等 auth 真正要读的键不能被脱敏破坏）")
    void nonSensitiveKeyShouldBeReturnedAsIs() {
        when(configMapper.selectOne(any(), any(Boolean.class)))
            .thenReturn(config("LOGIN_SMS_ENABLED", "true"));

        R<ConfigValue> response = controller.getConfigByKey("LOGIN_SMS_ENABLED");

        assertThat(response.getData().getConfigValue()).isEqualTo("true");
    }

    @Test
    @DisplayName("键不存在：返回空串（与既有契约一致，不返回 null）")
    void missingKeyShouldReturnEmptyString() {
        when(configMapper.selectOne(any(), any(Boolean.class))).thenReturn(null);

        R<ConfigValue> response = controller.getConfigByKey("NOT_EXIST_KEY");

        assertThat(response.getData().getConfigValue()).isEmpty();
    }

    private static SysConfig config(String configKey, String configValue) {
        SysConfig config = new SysConfig();
        config.setConfigKey(configKey);
        config.setConfigValue(configValue);
        return config;
    }
}
