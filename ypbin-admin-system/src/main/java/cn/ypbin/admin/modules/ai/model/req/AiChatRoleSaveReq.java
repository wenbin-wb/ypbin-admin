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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
/**
 * 角色新增/修改请求。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
public class AiChatRoleSaveReq {

    @NotBlank(message = "角色名称不能为空")
    private String name;

    private String description;

    private String avatar;

    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    /** 分类（assistant/translator/coder/analyst/writer/custom） */
    private String category;

    /** 推荐模型 */
    private String modelPreference;

    /** 默认温度（0.0 ~ 2.0） */
    private BigDecimal temperature;
}
