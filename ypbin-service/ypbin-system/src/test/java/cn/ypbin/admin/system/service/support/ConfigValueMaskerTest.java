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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigValueMasker} 的口径测试。
 *
 * <p><b>为什么需要它</b>：脱敏是「internal 打码、列表页明文」这个真实泄露面的修复点。判据（哪些键算敏感）
 * 与形态（打码成什么样）都必须被测试钉住，否则判据被改窄就会静默重新开口子——泄露面不会报错，
 * 只有用例能拦住。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class ConfigValueMaskerTest {

    @Test
    @DisplayName("判据：6 个敏感后缀全部命中（忽略大小写），非敏感键不命中")
    void sensitiveSuffixesShouldBeMatchedCaseInsensitively() {
        assertThat(ConfigValueMasker.isSensitive("SMS_ACCESS_KEY_SECRET")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("MAIL_PASSWORD")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("GITHUB_ACCESS_TOKEN")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("OSS_ACCESS_KEY")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("SIGN_PRIVATE_KEY")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("PROVIDER_API_KEY")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("sms_access_key_secret")).isTrue();
        // 非敏感：登录开关、模板 ID、账号名（不是密钥）
        assertThat(ConfigValueMasker.isSensitive("LOGIN_CAPTCHA_ENABLED")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("SMS_TEMPLATE_ID")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("MAIL_USERNAME")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("SITE_NAME")).isFalse();
        assertThat(ConfigValueMasker.isSensitive(null)).isFalse();
    }

    @Test
    @DisplayName("形态：敏感键保留末 4 位，长度 ≤ 4 整体打码；非敏感键原样返回")
    void maskShouldKeepLastFourCharactersOfSensitiveValues() {
        assertThat(ConfigValueMasker.mask("SMS_ACCESS_KEY_SECRET", "abcdefgh1234")).isEqualTo("****1234");
        assertThat(ConfigValueMasker.mask("MAIL_PASSWORD", "abcd")).isEqualTo("****");
        assertThat(ConfigValueMasker.mask("MAIL_PASSWORD", "a")).isEqualTo("****");
        assertThat(ConfigValueMasker.mask("MAIL_PASSWORD", "")).isEmpty();
        assertThat(ConfigValueMasker.mask("MAIL_PASSWORD", null)).isNull();
        assertThat(ConfigValueMasker.mask("SITE_NAME", "ypbin-admin")).isEqualTo("ypbin-admin");
        assertThat(ConfigValueMasker.mask(null, "raw")).isEqualTo("raw");
    }

    @Test
    @DisplayName("判据与形态同源：凡命中判据的键，mask 结果必然不是原文")
    void everySensitiveKeyShouldActuallyBeMasked() {
        // 覆盖库里全部内置敏感键 + 一个「只命中后缀、不属旧精确键清单」的键（正是历史泄露面）
        assertThat(ConfigValueMasker.mask("SMS_ACCESS_KEY_SECRET", "LTAI5tSecretValue"))
            .isNotEqualTo("LTAI5tSecretValue");
        assertThat(ConfigValueMasker.mask("MAIL_PASSWORD", "smtpAuthCode"))
            .isNotEqualTo("smtpAuthCode");
        assertThat(ConfigValueMasker.mask("SOCIAL_GITHUB_CLIENT_SECRET", "ghSecret"))
            .isNotEqualTo("ghSecret");
        // 历史泄露面样例：旧列表页只对 2 个精确键 + SOCIAL_*_CLIENT_SECRET 置空，
        // 这个键在列表页原本会**原样明文返回**，在 internal 却是打码的
        assertThat(ConfigValueMasker.mask("DINGTALK_APP_API_KEY", "dingTalkSecret"))
            .isNotEqualTo("dingTalkSecret");
    }

    @Test
    @DisplayName("写路径判据：mask 的产物必被识别为「未修改」，真实密钥不会被误判")
    void maskedValuesShouldBeRecognizableOnWritePath() {
        assertThat(ConfigValueMasker.isMaskedValue(ConfigValueMasker.mask("MAIL_PASSWORD", "smtpAuthCode")))
            .isTrue();
        assertThat(ConfigValueMasker.isMaskedValue("****1234")).isTrue();
        assertThat(ConfigValueMasker.isMaskedValue("****")).isTrue();
        // 真实密钥（不含 4 个星号开头）不得被误判为「未修改」
        assertThat(ConfigValueMasker.isMaskedValue("LTAI5tSecretValue")).isFalse();
        assertThat(ConfigValueMasker.isMaskedValue("***triple")).isFalse();
        assertThat(ConfigValueMasker.isMaskedValue(null)).isFalse();
        assertThat(ConfigValueMasker.isMaskedValue("")).isFalse();
    }
}
