/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 第三方登录配置解析测试。
 *
 * <p>读路径（{@code SocialConfigReader}）与管理面（{@code SocialConfigServiceImpl}）
 * 必须共用同一解析：历史上两侧各有一套，出现「列表里选得中、读取时却报错」。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
class SocialConfigKeysTest {

    @Test
    @DisplayName("true/false 与历史 1/0 都识别，且不区分大小写、容忍首尾空白")
    void shouldParseStoredEnabled() {
        assertThat(SocialConfigKeys.parseStoredEnabled("true", "github")).isTrue();
        assertThat(SocialConfigKeys.parseStoredEnabled(" TRUE ", "github")).isTrue();
        assertThat(SocialConfigKeys.parseStoredEnabled("1", "github")).isTrue();
        assertThat(SocialConfigKeys.parseStoredEnabled("false", "github")).isFalse();
        assertThat(SocialConfigKeys.parseStoredEnabled("0", "github")).isFalse();
    }

    @Test
    @DisplayName("其它取值是数据损坏：显式失败，不能静默按停用处理")
    void shouldRejectUnknownValue() {
        assertThatThrownBy(() -> SocialConfigKeys.parseStoredEnabled("yes", "github"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("github");
        assertThatThrownBy(() -> SocialConfigKeys.parseStoredEnabled(null, "github"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("从配置键反推平台标识；非启用键返回空串")
    void shouldResolveSourceFromKey() {
        assertThat(SocialConfigKeys.sourceOf("SOCIAL_GITHUB_ENABLED")).isEqualTo("github");
        assertThat(SocialConfigKeys.sourceOf("SOCIAL_GITHUB_CLIENT_ID")).isEmpty();
        assertThat(SocialConfigKeys.sourceOf(null)).isEmpty();
    }
}
