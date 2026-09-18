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
 * 留存矩阵中的一个单元。
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackRetentionCellResp {

    /** 相对首次出现日的天数偏移 */
    private Integer dayOffset;

    /** 该偏移日再次出现的去重用户数 */
    private Long userCount;

    /** 留存率（0~1，= userCount / 该行 cohortSize） */
    private BigDecimal retentionRate;
}
