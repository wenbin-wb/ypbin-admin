/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮件测试请求。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Data
public class MailTestReq {

    @NotBlank(message = "收件邮箱不能为空")
    @Email(message = "收件邮箱格式不正确")
    private String to;
}
