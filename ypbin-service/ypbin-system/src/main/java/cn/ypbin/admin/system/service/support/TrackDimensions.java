/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.support;

/**
 * 埋点聚合的维度约定。
 *
 * <p><strong>为什么需要哨兵值</strong>：{@code sys_track_event_daily} / {@code sys_track_user_daily} 的
 * 唯一键包含 {@code app_id}，而 MySQL 的唯一索引<strong>对 NULL 不去重</strong>——
 * 维度为空时若写 NULL，同一维度为空的多个分组会各自成行，重算后必然出现重复行，
 * 且 {@code ON DUPLICATE KEY UPDATE} 也拦不住。故本类给出统一的非空哨兵。</p>
 *
 * <p>哨兵只出现在聚合表里，明细表 {@code sys_track_event.app_id} 仍按 NULL 原样保留。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackDimensions {

    /**
     * 应用标识为空时的哨兵值。
     *
     * <p>长度必须不超过明细表 {@code app_id VARCHAR(64)} 的上限。若客户端真的上报了这个字面量，
     * 它会与「未设置」混为一行——这是哨兵方案的已知边界，取值刻意用带下划线的保留样式降低撞名概率。</p>
     */
    public static final String NONE_APP_ID = "__NONE__";

    private TrackDimensions() {
    }
}
