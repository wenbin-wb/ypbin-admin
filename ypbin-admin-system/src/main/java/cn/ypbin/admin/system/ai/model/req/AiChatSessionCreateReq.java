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

import lombok.Data;

/**
 * 创建对话会话请求。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Data
public class AiChatSessionCreateReq {

    /** 会话标题（可选，默认"新对话"） */
    private String title;

    /** 角色 ID（可选） */
    private Long roleId;

    /** 模型 ID（可选） */
    private Long modelId;
}
