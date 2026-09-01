/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * 编辑知识库请求。
 *
 * @author wenbin
 * @since 2026-08-17
 */
@Getter
@Setter
public class AiKnowledgeBaseUpdateReq {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    /** 图标（emoji 或图标名） */
    private String icon;

    private String remark;
}
