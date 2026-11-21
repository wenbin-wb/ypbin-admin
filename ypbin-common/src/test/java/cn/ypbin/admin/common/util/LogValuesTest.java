/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LogValues} 的净化口径测试。
 *
 * <p>核心判据（对应 CodeQL {@code java/log-injection} 的实质风险）：外部可控值里的 CR/LF
 * **必须**被替换，否则攻击者能伪造整条日志行。</p>
 *
 * @author wenbin
 * @since 2026-11-22
 */
class LogValuesTest {

    @Test
    @DisplayName("CR/LF/TAB 等控制字符被替换成空格，日志不可能被伪造出假行")
    void controlCharactersAreReplacedNotRemoved() {
        assertThat(LogValues.oneLine("a\r\nb")).isEqualTo("a  b");
        assertThat(LogValues.oneLine("/x\r\n2026-01-01 ERROR 登录成功 user=admin"))
            .isEqualTo("/x  2026-01-01 ERROR 登录成功 user=admin");
        assertThat(LogValues.oneLine("a\tb")).isEqualTo("a b");
        // 删除会让 "a\r\nb" 变成看起来合法的 "ab"，故必须是替换而非删除
        assertThat(LogValues.oneLine("a\r\nb")).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    @DisplayName("null 原样返回 null；普通值不变")
    void keepsNullAndPlainValues() {
        assertThat(LogValues.oneLine(null)).isNull();
        assertThat(LogValues.oneLine("/iot/devices?x=1")).isEqualTo("/iot/devices?x=1");
        assertThat(LogValues.oneLine(123)).isEqualTo("123");
    }

    @Test
    @DisplayName("超长值被截断并带标记（防日志行被撑爆）")
    void truncatesOverlongValues() {
        String longText = "x".repeat(LogValues.MAX_LENGTH + 100);
        String result = LogValues.oneLine(longText);
        assertThat(result).endsWith("…(truncated)");
        assertThat(result.length()).isLessThanOrEqualTo(LogValues.MAX_LENGTH + "…(truncated)".length());
    }
}
