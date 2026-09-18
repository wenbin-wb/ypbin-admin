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
 * 漏斗分析结果。
 *
 * <p><strong>为什么必须带上 {@code truncatedSessionCount}</strong>：会话的 {@code event_sequence}
 * 有界（最多 50 个事件，超出即截断）。被截断的会话可能<strong>丢失了后续步骤</strong>，
 * 它会被当成「没走到该步」——因此每一步的会话数是<strong>下限</strong>而不是精确值。
 * 不暴露这个前提，使用者会把漏斗数字当成精确值解读（方案第五节第 5 条：埋点语义是「至少」）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackFunnelResp {

    /** 各步骤的会话数与相对首步的转化率（按请求的步骤顺序） */
    private List<TrackFunnelStepResp> steps;

    /**
     * 参与本次漏斗判定的会话中，事件序列<strong>被截断</strong>的会话数。
     *
     * <p>大于 0 时各步 {@code sessionCount} 只是下限：这些会话的后续步骤可能被截掉，
     * 客户端应提示「实际完成数只会更多」。</p>
     */
    private Long truncatedSessionCount;
}
