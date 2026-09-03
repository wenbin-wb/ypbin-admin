/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.entity;

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户消息/站内信。租户隔离。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_message")
public class SysMessage extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 来源公告 ID，普通消息为空 */
    private Long noticeId;

    /** 公告发布版本，普通消息为空 */
    private Long publishVersion;

    /** 接收人用户 ID */
    private Long receiverUserId;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** 消息类型：1 系统通知、2 用户消息 */
    private Integer messageType;

    /** 是否已读：0 未读、1 已读 */
    private Integer readStatus;
}
