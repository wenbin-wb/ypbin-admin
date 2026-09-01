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

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * 重置用户密码请求（管理员操作）。
 *
 * @author wenbin
 * @since 2026-08-03
 */
@Getter
@Setter
public class ResetPasswordReq {

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    private String password;
}
