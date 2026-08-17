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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 知识库文档。
 *
 * <p>{@code status} 语义为向量化处理状态：0 处理中、1 就绪、2 失败
 * （由业务代码显式维护，基类不覆盖）。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
@TableName("ai_document")
public class AiDocument extends TenantBaseEntity {

    /** 所属知识库 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** 文件名 */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 切片数量 */
    private Integer chunkCount;

    /** 失败原因 */
    private String errorMsg;

    /** 原文件本地存储路径（供失败重试向量化时读取原文） */
    private String filePath;
}
