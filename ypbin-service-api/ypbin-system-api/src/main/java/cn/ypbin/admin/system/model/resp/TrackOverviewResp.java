/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 埋点概览响应。
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackOverviewResp {

    /** 事件总数（全部时间） */
    private Long totalEvents;

    /** 今日事件数 */
    private Long todayEvents;

    /** 近 7 天事件数 */
    private Long weekEvents;

    /** 近 7 天去重用户数（不含空用户，如匿名事件） */
    private Long weekUsers;

    /** 近 7 天失败事件数（success = 0） */
    private Long weekFailures;
}
