/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.model.resp;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 知识库响应。
 *
 * @author wenbin
 * @since 2026-09-04
 */
@Getter
@Setter
public class AiKnowledgeBaseResp {

    private Long id;

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 文档数量（冗余计数，避免 COUNT 查询） */
    private Integer docCount;

    /** 知识库图标（emoji 或图标名，供卡片展示） */
    private String icon;

    /** 备注 */
    private String remark;

    /** 网页挂件令牌（非空=启用对外公开问答；重置即轮换新令牌） */
    private String widgetToken;

    /** 挂件是否启用（0 未启用 / 1 已启用） */
    private Integer widgetEnabled;

    /** 公开分享令牌（非空=启用公开分享；关闭再开启即轮换新令牌） */
    private String shareToken;

    /** 分享是否启用（0 未启用 / 1 已启用） */
    private Integer shareEnabled;

    /** 分享过期时间（NULL=永不过期） */
    private LocalDateTime shareExpireTime;

    private LocalDateTime createTime;
}
