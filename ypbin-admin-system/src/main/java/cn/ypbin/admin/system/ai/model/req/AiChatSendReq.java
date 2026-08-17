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
import java.util.List;
import lombok.Data;

/**
 * 发送对话消息请求。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Data
public class AiChatSendReq {

    /** 会话 ID（新会话传 null，后端自动创建） */
    private Long sessionId;

    /** 角色 ID（指定角色，null 为默认助手） */
    private Long roleId;

    /** 模型 ID（指定模型，null 使用角色推荐或默认模型） */
    private Long modelId;

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 图片附件 URL 列表（多模态输入） */
    private List<String> images;

    /** 关联知识库 ID（RAG 检索增强对话，null 则不启用 RAG） */
    private Long knowledgeBaseId;

    /** 是否流式响应（true 返回 SSE，false 返回完整结果） */
    private Boolean stream;
}
