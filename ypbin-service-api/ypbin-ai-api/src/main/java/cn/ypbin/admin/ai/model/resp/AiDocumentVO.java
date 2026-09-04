/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.model.resp;

import cn.ypbin.admin.ai.entity.AiDocument;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库文档视图对象（对外响应，不暴露本地存储路径等敏感字段）。
 *
 * @author wenbin
 * @since 2026-08-17
 */
@Getter
@Setter
public class AiDocumentVO {

    private Long id;
    private Long knowledgeBaseId;
    private String filename;
    private Long fileSize;
    private Integer chunkCount;
    /**
     * 向量化处理状态：0 处理中、1 就绪、2 失败。
     */
    private Integer status;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String sourceType;
    private String sourceUrl;

    /** 从实体转换为 VO（屏蔽 filePath 等内部字段）。 */
    public static AiDocumentVO from(AiDocument doc) {
        AiDocumentVO vo = new AiDocumentVO();
        vo.setId(doc.getId());
        vo.setKnowledgeBaseId(doc.getKnowledgeBaseId());
        vo.setFilename(doc.getFilename());
        vo.setFileSize(doc.getFileSize());
        vo.setChunkCount(doc.getChunkCount());
        vo.setStatus(doc.getStatus());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setCreateTime(doc.getCreateTime());
        vo.setUpdateTime(doc.getUpdateTime());
        vo.setSourceType(doc.getSourceType());
        vo.setSourceUrl(doc.getSourceUrl());
        return vo;
    }
}
