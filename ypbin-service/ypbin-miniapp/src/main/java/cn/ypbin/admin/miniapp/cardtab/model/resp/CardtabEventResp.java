/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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

    /** 发生时间 */
    private LocalDateTime createdAt;
}
