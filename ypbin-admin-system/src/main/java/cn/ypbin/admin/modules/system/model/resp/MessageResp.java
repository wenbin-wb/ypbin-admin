/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.resp;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 用户消息/站内信响应。
 *
 * @author wenbin
 * @since 2026-09-04
 */
@Getter
@Setter
public class MessageResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 消息类型：1 系统通知、2 用户消息 */
    private Integer messageType;

    /** 是否已读：0 未读、1 已读 */
    private Integer readStatus;

    private LocalDateTime createTime;
}
