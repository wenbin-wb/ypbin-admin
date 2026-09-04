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
 * AI Prompt 模板响应。
 *
 * @author wenbin
 * @since 2026-09-04
 */
@Getter
@Setter
public class AiPromptTemplateResp {

    private Long id;

    /** 模板名称 */
    private String name;

    /** 分类，如 coding、writing、analysis */
    private String category;

    /** 提示词模板，支持 {username} 占位符 */
    private String template;

    /** 描述 */
    private String description;

    /** 状态：1 正常 0 停用 */
    private Integer status;

    private LocalDateTime createTime;
}
