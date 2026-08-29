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
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * AI 文档分块（向量化切片落库，供分块可视化与检索诊断）。
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Getter
@Setter
@TableName("ai_document_chunk")
public class AiDocumentChunk extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属知识库 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** 所属文档 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    /** 分块序号（0 起） */
    private Integer chunkIndex;

    /** 分块内容 */
    private String content;

    /** 字符数 */
    private Integer charCount;
}
