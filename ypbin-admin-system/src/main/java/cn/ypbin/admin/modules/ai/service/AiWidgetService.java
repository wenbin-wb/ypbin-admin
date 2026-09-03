/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.service;

import java.util.Map;

/**
 * 知识库网页挂件服务。
 *
 * <p>对外提供匿名可用的公开问答通道：通过知识库专属令牌（{@code widgetToken}）鉴权，
 * 未登录用户只能对持有令牌的知识库提问。内部通过 {@code TenantContext.runWithTenant}
 * 将请求限定在知识库所属租户，不泄露其它租户数据，也不绕过租户隔离。</p>
 *
 * @author wenbin
 * @since 2026-08-18
 */
public interface AiWidgetService {

    /**
     * 启用/停用知识库挂件。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param enabled         true 启用（生成新令牌）；false 停用（清除令牌）
     * @return 启用时返回新令牌，停用返回 null
     */
    String setWidgetEnabled(Long knowledgeBaseId, boolean enabled);

    /**
     * 按令牌查询挂件配置（知识库名称、是否启用）。
     *
     * @param token 知识库专属令牌
     * @return 配置映射（name/enabled），无效令牌抛业务异常
     */
    Map<String, Object> getConfig(String token);

    /**
     * 对指定令牌指向的知识库提问（非流式，RAG 增强）。
     *
     * @param token    知识库专属令牌
     * @param question 问题
     * @return AI 回答文本
     */
    String ask(String token, String question);
}
