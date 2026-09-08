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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 牌账清-房间实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("cardtab_room")
public class CardtabRoom extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 房主用户 ID */
    private Long ownerUserId;

    /** 房间名称 */
    private String name;

    /** 房间口令码 */
    private String roomCode;

    /** 玩法类型：MAHJONG/DOUDUIZHU/RUNFAST/CUSTOM */
    private String gameType;

    /** 计分单位 */
    private String unit;

    /** 记账模式：SELF_PAY_TO_MEMBER */
    private String bookkeepingMode;

    /** 是否允许口令加入：1 允许 0 禁止 */
    private Integer allowPasscodeJoin;

    /** 房间状态：ONGOING 进行中 SETTLED 已结算 CLOSED 已关闭 */
    private String roomStatus;

    /** 结算时间 */
    private LocalDateTime settledAt;
}

