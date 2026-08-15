/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增/编辑模型配置请求。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Data
public class AiModelConfigSaveReq {

    /** 模型显示名称 */
    @NotBlank(message = "模型名称不能为空")
    private String name;

    /** 提供商：openai | deepseek | ollama | custom */
    @NotBlank(message = "提供商不能为空")
    private String provider;

    /** API Key */
    private String apiKey;

    /** 接口基础地址（Ollama/自定义必填）*/
    private String baseUrl;

    /** 模型名称，如 deepseek-v4-flash、gpt-5.6 */
    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /** 备注 */
    private String remark;
}
