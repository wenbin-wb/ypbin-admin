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
 * AI 知识库。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
@TableName("ai_knowledge_base")
public class AiKnowledgeBase extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 文档数量（冗余计数，避免 COUNT 查询）*/
    private Integer docCount;

    /** 知识库图标（emoji 或图标名，供卡片展示） */
    private String icon;

    /** 备注 */
    private String remark;
}
