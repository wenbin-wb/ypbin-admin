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

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 留存矩阵的聚合点，由聚合 SQL 直接映射而来。
 *
 * <p>一行表示「{@code cohortDate} 这天首次出现的用户中，第 {@code dayOffset} 天又出现的去重人数」，
 * 即经典口径的「第 N 日留存」（只看第 N 日当天是否出现，不累计中间日期）。</p>
 *
 * <p>它是查询侧的内部投影，不直接作为接口出参：接口出参是补零后的完整矩阵
 * （见 {@link TrackRetentionResp}）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackRetentionPointResp {

    /** 首次出现日 */
    private LocalDate cohortDate;

    /** 相对首次出现日的天数偏移（0 表示首日自身） */
    private Integer dayOffset;

    /** 该偏移日再次出现的去重用户数 */
    private Long userCount;
}
