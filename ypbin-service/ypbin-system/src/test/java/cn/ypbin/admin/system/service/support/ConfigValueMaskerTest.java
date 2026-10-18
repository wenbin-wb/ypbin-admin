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

import java.util.List;
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

    /** 验收者实测「两侧都明文」的 6 个键：判据缺口的核心回归样例 */
    private static final List<String> GAP_KEYS = List.of(
        "ALIYUN_SECRET_KEY", "MY_SECRET_KEY", "DB_PASSWD", "SERVICE_PWD",
        "THIRD_PARTY_CREDENTIAL", "SOCIAL_ALIPAY_PUBLIC_KEY");

    @Test
    @DisplayName("判据：凭据类后缀全部命中（含历史缺口 6 键），标识符类键不命中")
    void sensitiveSuffixesShouldBeMatchedCaseInsensitively() {
        // 凭据类后缀逐一命中
        assertThat(ConfigValueMasker.isSensitive("SMS_ACCESS_KEY_SECRET")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("MAIL_PASSWORD")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("GITHUB_ACCESS_TOKEN")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("OSS_ACCESS_KEY")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("SIGN_PRIVATE_KEY")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("PROVIDER_API_KEY")).isTrue();
        assertThat(ConfigValueMasker.isSensitive("sms_access_key_secret")).isTrue();
        // 历史缺口（验收者探针实测为 false 的 6 个键）：_KEY / _PASSWD / _CREDENTIAL 覆盖
        assertThat(GAP_KEYS).allSatisfy(key -> assertThat(ConfigValueMasker.isSensitive(key))
            .as("历史缺口：%s 必须判为敏感（否则列表页与 internal 都会明文回显）", key)
            .isTrue());
        // 标识符类：单独不能通过认证，保持明文（打码会让运维无法确认「配的是哪一把」）
        assertThat(ConfigValueMasker.isSensitive("SMS_ACCESS_KEY_ID")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("SOCIAL_GITHUB_CLIENT_ID")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("MAIL_USERNAME")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("LOGIN_CAPTCHA_ENABLED")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("SMS_TEMPLATE_ID")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("SITE_NAME")).isFalse();
        assertThat(ConfigValueMasker.isSensitive("MAIL_FROM")).isFalse();
        assertThat(ConfigValueMasker.isSensitive(null)).isFalse();
    }

    @Test
    @DisplayName("种子数据误伤核对：只有凭据类键被打码，标识符/阈值类键保持明文")
    void seedDataKeysShouldOnlyMaskCredentials() {
        // 会被打码（部署种子里的全部凭据类键）
        assertThat(List.of("MAIL_PASSWORD", "SMS_ACCESS_KEY_SECRET", "SOCIAL_GITHUB_CLIENT_SECRET",
            "SOCIAL_ALIPAY_CLIENT_SECRET", "SOCIAL_ALIPAY_PUBLIC_KEY"))
            .allSatisfy(key -> assertThat(ConfigValueMasker.isSensitive(key)).isTrue());
        // 必须保持明文（误伤会让运维看不到配置、也影响 auth 读取开关）
        assertThat(List.of("SMS_ACCESS_KEY_ID", "MAIL_USERNAME", "MAIL_FROM", "MAIL_FROM_NAME",
            "MAIL_HOST", "MAIL_PORT", "MAIL_SSL_ENABLED", "SITE_NAME", "SMS_SIGNATURE",
            "SMS_SUPPLIER", "SMS_TEMPLATE_ID", "SOCIAL_GITHUB_CLIENT_ID", "SOCIAL_GITHUB_REDIRECT_URI",
            "LOGIN_CAPTCHA_ENABLED", "LOGIN_SMS_ENABLED", "PASSWORD_MIN_LENGTH",
            "PASSWORD_REQUIRE_DIGIT", "PASSWORD_ERROR_LOCK_COUNT"))
            .allSatisfy(key -> assertThat(ConfigValueMasker.isSensitive(key))
                .as("种子键 %s 不应被打码（非凭据类）", key)
                .isFalse());
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
    @DisplayName("判据与形态同源：凡命中判据的键，mask 结果必然是 ****末4位（不是原文）")
    void everySensitiveKeyShouldActuallyBeMasked() {
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
        // 历史缺口 6 键：同一份值经 mask 必须是 ****末4位
        assertThat(ConfigValueMasker.mask("ALIYUN_SECRET_KEY", "aliyunSecret0001"))
            .isEqualTo("****0001");
        assertThat(ConfigValueMasker.mask("MY_SECRET_KEY", "mySecretKey9999"))
            .isEqualTo("****9999");
        assertThat(ConfigValueMasker.mask("DB_PASSWD", "dbPasswdValue7777"))
            .isEqualTo("****7777");
        assertThat(ConfigValueMasker.mask("SERVICE_PWD", "svcPwdValue6666"))
            .isEqualTo("****6666");
        assertThat(ConfigValueMasker.mask("THIRD_PARTY_CREDENTIAL", "thirdParty5555"))
            .isEqualTo("****5555");
        assertThat(ConfigValueMasker.mask("SOCIAL_ALIPAY_PUBLIC_KEY", "alipayPublicKey3333"))
            .isEqualTo("****3333");
        // 标识符保持明文（不打码才有运维价值）
        assertThat(ConfigValueMasker.mask("SMS_ACCESS_KEY_ID", "LTAI5tAccessKeyId")).isEqualTo("LTAI5tAccessKeyId");
    }

    @Test
    @DisplayName("写路径判据：mask 的产物必被识别为「未修改」，真实密钥不会被误判")
    void maskedValuesShouldBeRecognizableOnWritePath() {
        assertThat(ConfigValueMasker.isMaskedValue(ConfigValueMasker.mask("MAIL_PASSWORD", "smtpAuthCode")))
            .isTrue();
        assertThat(ConfigValueMasker.isMaskedValue("****1234")).isTrue();
        assertThat(ConfigValueMasker.isMaskedValue("****")).isTrue();
        // 前导/尾随空格不得绕过「未修改」识别（否则掩码会被当真实值写库）
        assertThat(ConfigValueMasker.isMaskedValue(" ****0001")).isTrue();
        assertThat(ConfigValueMasker.isMaskedValue("****0001 ")).isTrue();
        assertThat(ConfigValueMasker.isMaskedValue("\t****0001\n")).isTrue();
        // 真实密钥（不以 4 个星号开头）不得被误判为「未修改」
        assertThat(ConfigValueMasker.isMaskedValue("LTAI5tSecretValue")).isFalse();
        assertThat(ConfigValueMasker.isMaskedValue("***triple")).isFalse();
        assertThat(ConfigValueMasker.isMaskedValue(null)).isFalse();
        assertThat(ConfigValueMasker.isMaskedValue("")).isFalse();
        assertThat(ConfigValueMasker.isMaskedValue("   ")).isFalse();
    }
}
