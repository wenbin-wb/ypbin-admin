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

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.jsoup.Connection;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * 知识库抓取客户端：对用户提交 URL 的 HTTP 抓取统一收口，并施加瞬时故障重试。
 *
 * <p>重试使用 Spring Framework 7 内建的 {@link Retryable}（{@code org.springframework.resilience.annotation}），
 * 无需引入 Spring Retry 或 Resilience4j：仅对网络类瞬时故障（超时/IO）重试，退避递增并带抖动，
 * 避免瞬时抖动直接失败、又不对目标造成脉冲式压力。</p>
 *
 * <p><strong>不重试业务性拒绝：</strong>SSRF 校验失败、协议不合法、4xx/5xx 等抛出的
 * {@code BusinessException} 不在 {@code includes} 内，不会被重试——重试无法改变结果，只会放大请求。</p>
 *
 * <p>本 Bean 独立于 {@link AiKnowledgeImportComponent}，保证 {@code @Retryable} 生效：
 * Spring AOP 基于代理，同类内部自调用不经过代理，重试不会触发；因此两个入口方法各自标注注解，
 * 共用私有实现而不互相调用。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@Component
public class AiUrlFetcher {

    /** 瞬时网络故障重试参数：首退避 300ms，翻倍递增，上限 2s，抖动 100ms，共 3 次机会 */
    private static final int MAX_RETRIES = 2;

    private static final long RETRY_DELAY_MILLIS = 300;

    private static final double RETRY_MULTIPLIER = 2.0;

    private static final long RETRY_MAX_DELAY_MILLIS = 2000;

    private static final long RETRY_JITTER_MILLIS = 100;

    /**
     * 抓取目标 URL：先做 SSRF/协议校验，再发起请求并校验响应状态。
     *
     * @param url 目标地址
     * @return Jsoup 响应
     * @throws IOException 网络类异常（触发重试）
     */
    @Retryable(includes = {IOException.class, SocketTimeoutException.class},
        maxRetries = MAX_RETRIES, delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER,
        maxDelay = RETRY_MAX_DELAY_MILLIS, jitter = RETRY_JITTER_MILLIS)
    public Connection.Response fetch(String url) throws IOException {
        return doFetch(url, false);
    }

    /**
     * 抓取目标 URL 并忽略内容类型校验（用于 sitemap / RSS 等 XML 资源）。
     *
     * @param url 目标地址
     * @return Jsoup 响应
     * @throws IOException 网络类异常（触发重试）
     */
    @Retryable(includes = {IOException.class, SocketTimeoutException.class},
        maxRetries = MAX_RETRIES, delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER,
        maxDelay = RETRY_MAX_DELAY_MILLIS, jitter = RETRY_JITTER_MILLIS)
    public Connection.Response fetchIgnoringContentType(String url) throws IOException {
        return doFetch(url, true);
    }

    private Connection.Response doFetch(String url, boolean ignoreContentType) throws IOException {
        Connection connection = AiKnowledgeImportComponent.guardedConnection(url);
        if (ignoreContentType) {
            connection = connection.ignoreContentType(true);
        }
        Connection.Response response = connection.execute();
        AiKnowledgeImportComponent.ensureSuccessful(response, url);
        return response;
    }
}
