/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.write.style.ColumnWidth;

/**
 * 埋点事件导出模型。
 *
 * <p>只导出分析所需的列：事件属性（{@code payload}）与 User-Agent 原串不进 Excel——
 * 前者结构不固定会让表格失去可读性，后者整串对分析无意义。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Getter
@Setter
public class TrackEventExportVo {

    /** 事件码 */
    @ExcelProperty("事件码")
    @ColumnWidth(26)
    private String eventCode;

    /** 应用标识 */
    @ExcelProperty("应用")
    @ColumnWidth(18)
    private String appId;

    /** 用户 ID */
    @ExcelProperty("用户 ID")
    @ColumnWidth(18)
    private Long userId;

    /** 会话 ID */
    @ExcelProperty("会话 ID")
    @ColumnWidth(22)
    private String sessionId;

    /** 页面地址 */
    @ExcelProperty("页面")
    @ColumnWidth(40)
    private String pageUrl;

    /** 耗时（毫秒） */
    @ExcelProperty("耗时(ms)")
    @ColumnWidth(12)
    private Long durationMs;

    /** 结果 */
    @ExcelProperty("结果")
    @ColumnWidth(10)
    private String status;

    /** 服务端接收时间 */
    @ExcelProperty("接收时间")
    @ColumnWidth(22)
    private LocalDateTime receivedTime;
}
