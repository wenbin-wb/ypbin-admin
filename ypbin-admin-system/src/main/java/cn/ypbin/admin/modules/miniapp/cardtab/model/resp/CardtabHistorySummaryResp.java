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

import lombok.Getter;
import lombok.Setter;

/**
 * 历史房间的月度统计出参。
 *
 * <p>历史列表是分页的，前端只拿第一页做统计会少算；这里由服务端按自然月聚合，
 * 不受列表分页上限影响。</p>
 *
 * @author wenbin
 * @since 2026-11-13
 */
@Getter
@Setter
public class CardtabHistorySummaryResp {

    /** 统计月份，格式 yyyy-MM */
    private String month;

    /** 本月参与过的房间数 */
    private Integer roomCount;

    /** 本月房间的支出总笔数 */
    private Integer expenseCount;
}
