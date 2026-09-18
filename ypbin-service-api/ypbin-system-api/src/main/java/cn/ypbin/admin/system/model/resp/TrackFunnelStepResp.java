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
 * 漏斗单步结果。
 *
 * <p>{@code conversionRate} 是<strong>相对首步</strong>的转化率（首步恒为 1）。序列化时受
 * 全局「大数字转字符串」开关影响，会输出字符串而非 JSON number。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackFunnelStepResp {

    /** 步骤序号（从 1 开始） */
    private Integer stepIndex;

    /** 步骤事件码 */
    private String eventCode;

    /** 走到本步的会话数 */
    private Long sessionCount;

    /** 相对首步的转化率（0~1，首步为 1） */
    private BigDecimal conversionRate;
}
