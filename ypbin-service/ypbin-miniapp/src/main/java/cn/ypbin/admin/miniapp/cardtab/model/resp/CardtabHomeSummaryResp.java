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
 * <p><b>字段命名说明</b>：本类是端侧视图契约，字段名以「消费方读得懂且名副其实」为准。
 * 历史版本曾用 {@code todayScoreChange}/{@code monthRoomCount} 承载「我的总积分/累计场次」，
 * 值与名不符（既不是"今日变动"也不是"本月"），已按实际语义更名为 {@code myScore}/{@code roomCount}——
 * 这不是为迁就调用方而改名，而是让名字与它真正装的东西一致。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabHomeSummaryResp {

    /** 进行中的房间（若有） */
    private CardtabRoomDetailResp current;

    /** 最近参与的房间列表 */
    private List<CardtabRoomDetailResp> recentRooms;

    /** 我的总积分（各房间积分之和） */
    private BigDecimal myScore = BigDecimal.ZERO;

    /** 累计参与的场次 */
    private Integer roomCount = 0;

    /** 待结算房间数 */
    private Integer ongoingCount = 0;

    /** 今日记账笔数（我参与的房间） */
    private Integer todayExpenseCount = 0;
}
