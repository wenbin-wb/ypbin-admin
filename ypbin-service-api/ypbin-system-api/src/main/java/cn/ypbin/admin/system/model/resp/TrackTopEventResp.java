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
 * 埋点事件 Top 排行响应。
 *
 * <p>只回事件码与次数，**不带中文描述**：事件目录的唯一事实源在 starter 仓（前端已由生成器同步），
 * 后端再存一份必然漂移。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackTopEventResp {

    /** 事件码 */
    private String eventCode;

    /** 事件数 */
    private Long count;
}
