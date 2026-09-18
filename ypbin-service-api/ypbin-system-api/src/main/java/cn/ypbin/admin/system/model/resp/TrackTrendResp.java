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
 * 埋点事件按天聚合响应（趋势图用）。
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackTrendResp {

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 当日事件数 */
    private Long count;
}
