/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.vo;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.write.style.ColumnWidth;

/**
 * 用户导出 Excel 数据模型。
 *
 * @author wenbin
 * @since 2026-08-27
 */
@Getter
@Setter
public class UserExportVo implements Serializable {

    @ColumnWidth(20)
    @ExcelProperty("用户名")
    private String username;

    @ColumnWidth(20)
    @ExcelProperty("真实姓名")
    private String realName;

    @ColumnWidth(20)
    @ExcelProperty("昵称")
    private String nickname;

    @ColumnWidth(25)
    @ExcelProperty("所属部门")
    private String deptName;

    @ColumnWidth(20)
    @ExcelProperty("手机号码")
    private String phone;

    @ColumnWidth(25)
    @ExcelProperty("电子邮箱")
    private String email;

    @ColumnWidth(12)
    @ExcelProperty("性别")
    private String gender;

    @ColumnWidth(12)
    @ExcelProperty("状态")
    private String status;

    @ColumnWidth(25)
    @ExcelProperty("创建时间")
    private String createTime;
}
