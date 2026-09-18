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

import java.util.List;

/**
 * 会话事件序列的拼接与截断。
 *
 * <p>序列是<strong>有界</strong>的：一个异常长的会话（客户端一直不刷新页面）会把行撑大，
 * 也会让漏斗查询把整段序列读进内存，因此最多保留 {@link #MAX_SEQUENCE_EVENTS} 个事件码，
 * 超出即截断并把 {@code truncated} 标记为真。</p>
 *
 * <p><strong>截断的语义后果</strong>：被截断的会话可能在截断点之后才完成某个漏斗步骤，
 * 于是它会被算成「未完成」。因此漏斗数字是<strong>不算多</strong>的下界，而不是精确值。</p>
 *
 * <p>抽成独立类是为了能在<strong>不依赖数据库</strong>的前提下测试拼接、截断与分隔符边界。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class TrackEventSequenceBuilder {

    /** 序列中保留的最大事件数 */
    public static final int MAX_SEQUENCE_EVENTS = 50;

    /** 序列分隔符 */
    public static final String SEQUENCE_SEPARATOR = ",";

    private TrackEventSequenceBuilder() {
    }

    /**
     * 拼接后的序列与截断标记。
     *
     * @param value     逗号分隔的事件码序列
     * @param truncated 是否因超过上限被截断
     */
    public record Sequence(String value, boolean truncated) {
    }

    /**
     * 按时间顺序拼接事件码，超过上限即截断。
     *
     * @param eventCodes 已按时间升序排列的事件码
     * @return 序列与截断标记
     * @throws IllegalArgumentException 事件码为空、或含有分隔符（会破坏序列的可解析性）
     */
    public static Sequence build(List<String> eventCodes) {
        if (eventCodes == null) {
            throw new IllegalArgumentException("事件码列表不能为 null");
        }
        int keep = Math.min(eventCodes.size(), MAX_SEQUENCE_EVENTS);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < keep; index++) {
            String eventCode = eventCodes.get(index);
            if (eventCode == null || eventCode.isEmpty()) {
                throw new IllegalArgumentException("第 " + index + " 个事件码为空，无法拼接会话序列");
            }
            if (eventCode.contains(SEQUENCE_SEPARATOR)) {
                // 显式报错而不是替换掉分隔符：替换属静默篡改数据，会让漏斗结果无法与明细表对齐
                throw new IllegalArgumentException("事件码「" + eventCode + "」含有分隔符「"
                    + SEQUENCE_SEPARATOR + "」，无法拼接会话序列");
            }
            if (index > 0) {
                builder.append(SEQUENCE_SEPARATOR);
            }
            builder.append(eventCode);
        }
        return new Sequence(builder.toString(), eventCodes.size() > MAX_SEQUENCE_EVENTS);
    }

    /**
     * 把序列还原成事件码列表。
     *
     * @param sequence 逗号分隔的事件码序列
     * @return 事件码列表；序列为空或空白时返回空集合
     */
    public static List<String> parse(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return List.of();
        }
        return List.of(sequence.split(SEQUENCE_SEPARATOR, -1));
    }
}
