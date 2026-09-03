/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * 新增/编辑 Prompt 模板请求。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
public class AiPromptTemplateSaveReq {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String category;

    @NotBlank(message = "提示词内容不能为空")
    private String template;

    private String description;
}
