/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 强制改密会话键的跨模块契约：auth/system 用 {@code PwdChangeKeys.REQUIRED} 写，
 * 网关因不依赖 {@code ypbin-common} 只能保留字面量，两处必须完全一致。
 *
 * <p>不一致时不会编译失败，只会在运行时表现为「登录后仍能访问受限接口」或「改密后一直被拦」，
 * 因此用源码级断言钉住。</p>
 *
 * @author wenbin
 * @since 2026-11-02
 */
class PwdChangeKeyContractTest {

    private static final Pattern COMMON_KEY = Pattern.compile(
        "REQUIRED\\s*=\\s*\"([^\"]+)\"");

    private static final Pattern GATEWAY_KEY = Pattern.compile(
        "PWD_CHANGE_REQUIRED_KEY\\s*=\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("★ 强制改密会话键：common 与 gateway 两处字面量必须一致")
    void pwdChangeSessionKeyMustMatchAcrossModules() throws IOException {
        String commonKey = extract(SourceScan.repoRoot().resolve(
            "ypbin-common/src/main/java/cn/ypbin/admin/common/support/PwdChangeKeys.java"),
            COMMON_KEY);
        String gatewayKey = extract(SourceScan.repoRoot().resolve(
            "ypbin-gateway/src/main/java/cn/ypbin/admin/gateway/auth/SaTokenGatewayAuthProvider.java"),
            GATEWAY_KEY);

        assertThat(gatewayKey)
            .as("网关的 PWD_CHANGE_REQUIRED_KEY 必须与 PwdChangeKeys.REQUIRED 一致；"
                + "不一致会导致强制改密拦截失效或误拦")
            .isEqualTo(commonKey);
    }

    private String extract(Path file, Pattern pattern) throws IOException {
        assertThat(Files.isRegularFile(file)).as("源文件不存在：%s", file).isTrue();
        Matcher matcher = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
        assertThat(matcher.find()).as("未找到会话键定义：%s", file).isTrue();
        return matcher.group(1);
    }
}
