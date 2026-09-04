/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.model.resp;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 对话消息响应。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
public class AiChatMessageResp {

    private Long id;

    /** 角色（user/assistant/system/tool） */
    private String role;

    /** 消息内容 */
    private String content;

    /** token 消耗 */
    private Integer tokens;

    /** 模型名称 */
    private String modelName;

    /** 结束原因 */
    private String finishReason;

    /** 工具调用记录（JSON 字符串） */
    private String toolCalls;

    /** 图片附件 */
    private List<String> images;

    /** 创建时间 */
    private LocalDateTime createTime;
}
