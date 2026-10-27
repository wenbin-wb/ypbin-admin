/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AI 模型连通性所用的 HTTP 客户端的**配置级**门禁（不做真实网络调用）。
 *
 * <p>为什么要有这一组用例：{@code testConnection} 的调用形态恰好是"**新连接 + 带体 POST**"，
 * 而 {@code baseUrl} 允许用户填**明文 {@code http://}**。JDK {@code HttpClient} 的默认协议是 HTTP/2
 * ⇒ 明文下会走 h2c upgrade，**在"服务端接受 upgrade 随后断开"那一类服务端上**会失败
 * （已在 EMQX/Cowboy 上实测：{@code java.io.IOException: EOF reached while reading}；
 * 判据见 {@code deploy/emqx/diagnose-emqx-admin-h2c/}）。
 * ⚠️ 但**不是所有明文服务端都会失败**——不对 upgrade 做 h2c 切换的服务端下，JDK 会优雅回落 HTTP/1.1
 * （独立复核用朴素 JDK {@code HttpServer} 实测），所以这里断言的是"客户端**不要去请求 h2c**"这一可判定的属性，
 * 而不是"报错必然复现"。同时，原先每次调用都 {@code newBuilder()} 新建客户端，
 * 会丢掉连接池、并把超时策略散落到调用点。</p>
 *
 * <p>这三条断言都很"小"，但它们挡的是**会被整体删掉的一行**（{@code .version(...)}）与
 * **会被顺手改回"每次新建"**的写法 —— 所以每条都做过变异验证（见 PR 描述）。</p>
 *
 * @author wenbin
 * @since 2026-09-27
 */
class AiModelConfigServiceImplTest {

    @Test
    @DisplayName("HTTP 客户端必须显式 HTTP/1.1（默认 HTTP/2 在明文 http:// 上会踩 h2c + 带体 POST 的坑）")
    void httpClientMustBeConfiguredForHttp11() {
        HttpClient.Version version = AiModelConfigServiceImpl.httpClient().version();

        assertThat(version)
            .as("必须显式 HTTP_1_1：JDK 默认 HTTP_2 下会发起 h2c upgrade，"
                + "而带体 POST 作为新连接首个请求在「接受 upgrade 再断」的服务端（如 EMQX/Cowboy）上会失败")
            .isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    @DisplayName("HTTP 客户端必须被复用（单例）：每次调用新建会丢掉连接池")
    void httpClientMustBeReused() {
        assertThat(AiModelConfigServiceImpl.httpClient())
            .as("必须复用同一个实例；每次调用 newBuilder() 会丢掉连接池，也让超时/协议策略散落各处")
            .isSameAs(AiModelConfigServiceImpl.httpClient());
    }

    @Test
    @DisplayName("必须显式配置连接超时（禁无超时的默认客户端）")
    void httpClientMustHaveExplicitConnectTimeout() {
        Duration timeout = AiModelConfigServiceImpl.httpClient().connectTimeout().orElse(null);

        assertThat(timeout)
            .as("连接超时必须显式设置（仓内铁律：远程调用禁止无超时默认客户端）")
            .isEqualTo(Duration.ofSeconds(AiModelConfigServiceImpl.CONNECT_TIMEOUT_SECONDS));
    }
}
