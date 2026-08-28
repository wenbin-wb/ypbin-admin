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
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;

/**
 * 用户批量导入 Excel 模板数据模型。
 *
 * @author wenbin
 * @since 2026-08-27
 */
@Data
public class UserImportVo implements Serializable {

    @ExcelProperty("用户名（必填）")
    private String username;

    @ExcelProperty("真实姓名（必填）")
    private String realName;

    @ExcelProperty("手机号码")
    private String phone;

    @ExcelProperty("电子邮箱")
    private String email;

    @ExcelProperty("初始密码（留空默认 123456）")
    private String password;
}
