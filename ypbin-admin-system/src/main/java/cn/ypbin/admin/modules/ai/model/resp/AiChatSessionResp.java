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
 * 对话会话响应。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
public class AiChatSessionResp {

    private Long id;

    /** 会话标题 */
    private String title;

    /** 绑定角色 ID */
    private Long roleId;

    /** 角色名称 */
    private String roleName;

    /** 角色头像 */
    private String roleAvatar;

    /** 消息总数 */
    private Integer messageCount;

    /** 累计 token */
    private Integer totalTokens;

    /** 是否置顶 */
    private Integer isPinned;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
