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

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 留存摘要列（D1/D7/D30 这类单列指标）。
 *
 * <p>它是<strong>跨多个首次出现日合并</strong>后的整体留存：
 * {@code retentionRate = userCount / cohortSize}，其中分母是参与本次统计且该偏移日已到观察窗口的
 * 所有首次出现日的基数之和——按基数加权，而不是把各行留存率再取平均（后者会让小样本行与大样本行等权）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackRetentionSummaryResp {

    /** 相对首次出现日的天数偏移 */
    private Integer dayOffset;

    /** 参与统计的首次出现用户数之和（分母） */
    private Long cohortSize;

    /** 该偏移日再次出现的去重用户数之和（分子） */
    private Long userCount;

    /** 加权留存率（0~1） */
    private BigDecimal retentionRate;
}
