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

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.write.style.ColumnWidth;

/**
 * 操作日志导出 Excel 数据模型。
 *
 * @author wenbin
 * @since 2026-08-27
 */
@Getter
@Setter
public class LogExportVo implements Serializable {

    @ColumnWidth(20)
    @ExcelProperty("所属模块")
    private String module;

    @ColumnWidth(25)
    @ExcelProperty("操作内容")
    private String value;

    @ColumnWidth(20)
    @ExcelProperty("操作人")
    private String operatorName;

    @ColumnWidth(20)
    @ExcelProperty("IP 地址")
    private String ip;

    @ColumnWidth(20)
    @ExcelProperty("IP 归属地")
    private String location;

    @ColumnWidth(15)
    @ExcelProperty("操作状态")
    private String status;

    @ColumnWidth(15)
    @ExcelProperty("耗时(ms)")
    private Long costTime;

    @ColumnWidth(25)
    @ExcelProperty("操作时间")
    private String createTime;
}
