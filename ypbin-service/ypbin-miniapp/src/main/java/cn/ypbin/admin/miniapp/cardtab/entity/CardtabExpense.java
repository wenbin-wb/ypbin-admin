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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 牌账清-支出记录实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("cardtab_expense")
public class CardtabExpense extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 房间 ID */
    private Long roomId;

    /** 支出成员 ID */
    private Long fromMemberId;

    /** 收款成员 ID */
    private Long toMemberId;

    /** 金额/积分 */
    private BigDecimal amount;

    /** 备注说明 */
    private String note;

    /** 状态：NORMAL 正常 REVOKED 已撤销 */
    private String expenseStatus;

    /** 撤销时间 */
    private LocalDateTime revokedAt;
}

