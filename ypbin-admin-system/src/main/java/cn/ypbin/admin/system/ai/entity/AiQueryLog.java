/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.entity;

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 检索问答日志（用于统计搜索热词与问答趋势）。
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Getter
@Setter
@TableName("ai_query_log")
public class AiQueryLog extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属知识库 ID */
    private Long knowledgeBaseId;

    /** 检索/问答问题原文 */
    private String query;

    /**
     * 来源：QUERY 知识库问答 / SEARCH 检索测试 / RERANK 重排测试 / MULTIPLE 多库测试。
     */
    private String source;
}
