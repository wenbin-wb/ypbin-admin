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
import lombok.Getter;
import lombok.Setter;

/**
 * 牌账清-结算快照实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("cardtab_settlement_snapshot")
public class CardtabSettlementSnapshot extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 房间 ID */
    private Long roomId;

    /** 结算快照数据（JSON 格式） */
    private String snapshotJson;
}

