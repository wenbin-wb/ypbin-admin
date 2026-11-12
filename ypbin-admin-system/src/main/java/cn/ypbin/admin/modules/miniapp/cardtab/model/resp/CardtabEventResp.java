/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.model.resp;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 房间流水事件出参。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabEventResp {

    /** 流水 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 事件类型 */
    private String eventType;

    /** 触发事件的成员 ID（支出流水中即支出人） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long memberId;

    /** 记录人（操作者）的用户 ID：多人可记模式下可能与支出人不同，用于判断撤销权限 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recorderUserId;

    /** 关联业务主键（支出流水即支出记录 ID），撤销时回传给撤销接口 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refId;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 涉及金额 */
    private BigDecimal amount;

    /** 支出人昵称 */
    private String fromMemberName;

    /** 收款人昵称 */
    private String toMemberName;

    /** 发生时间（与 {@code cardtab_room_event.create_time} 列同名） */
    private LocalDateTime createTime;
}
