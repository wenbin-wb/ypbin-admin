/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 牌账清-房间流水事件实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("cardtab_room_event")
public class CardtabRoomEvent extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 房间 ID */
    private Long roomId;

    /** 事件类型：ROOM_CREATED/MEMBER_JOINED/MEMBER_LEFT/EXPENSE_CREATED/EXPENSE_REVOKED/ROOM_SETTLED */
    private String eventType;

    /** 关联成员 ID */
    private Long memberId;

    /** 关联业务记录 ID */
    private Long refId;

    /** 标题 */
    private String title;

    /** 描述内容 */
    private String content;

    /** 涉及金额 */
    private BigDecimal amount;

    /** 支出人昵称 */
    private String fromMemberName;

    /** 收款人昵称 */
    private String toMemberName;
}

