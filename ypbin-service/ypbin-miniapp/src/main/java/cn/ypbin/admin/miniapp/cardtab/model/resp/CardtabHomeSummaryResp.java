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

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 首页概览出参。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabHomeSummaryResp {

    /** 进行中的房间（若有） */
    private CardtabRoomDetailResp ongoingRoom;

    /** 最近参与的房间列表 */
    private List<CardtabRoomDetailResp> recentRooms;

    /** 今日积分变动 */
    private BigDecimal todayScoreChange = BigDecimal.ZERO;

    /** 本月开局场次 */
    private Integer monthRoomCount = 0;

    /** 待结算房间数 */
    private Integer unsettledCount = 0;
}
