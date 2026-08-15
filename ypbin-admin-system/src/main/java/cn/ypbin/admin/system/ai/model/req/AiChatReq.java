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
 * 发送 AI 消息请求。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Data
public class AiChatReq {

    /** 会话 ID；null 时由后端自动创建新会话 */
    private Long conversationId;

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 关联知识库 ID（可选，启用 RAG 检索增强）*/
    private Long knowledgeBaseId;

    /** Prompt 模板 ID（可选，覆盖系统默认 system prompt）*/
    private Long promptTemplateId;
}
