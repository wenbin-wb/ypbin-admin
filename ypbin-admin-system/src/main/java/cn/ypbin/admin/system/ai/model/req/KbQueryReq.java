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
import lombok.Data;

/**
 * 知识库问答请求。
 *
 * @author wenbin
 * @since 2026-08-17
 */
@Data
public class KbQueryReq {

    @NotBlank(message = "问题不能为空")
    private String question;
}
