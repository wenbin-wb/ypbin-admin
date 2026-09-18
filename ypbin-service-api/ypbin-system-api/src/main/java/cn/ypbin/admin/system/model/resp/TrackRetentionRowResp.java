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

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 留存矩阵的一行：一个「首次出现日」及其后续各日的留存。
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackRetentionRowResp {

    /** 首次出现日（yyyy-MM-dd） */
    private String cohortDate;

    /** 该日首次出现的去重用户数（D0 基数） */
    private Long cohortSize;

    /** 各偏移日的留存单元（只包含已到观察窗口的偏移日） */
    private List<TrackRetentionCellResp> cells;
}
