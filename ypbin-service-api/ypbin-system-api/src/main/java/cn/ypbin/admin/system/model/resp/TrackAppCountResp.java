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
 * 埋点应用维度计数响应。
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackAppCountResp {

    /** 应用标识（事件未带 appId 时为空，由前端展示为"未设置"） */
    private String appId;

    /** 事件数 */
    private Long count;
}
