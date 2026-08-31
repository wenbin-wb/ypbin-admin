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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
/**
 * 推送测试请求。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Getter
@Setter
public class PushTestReq {

    @NotNull(message = "用户 ID 不能为空")
    @Positive(message = "用户 ID 必须为正数")
    private Long userId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 500, message = "消息内容不能超过 500 个字符")
    private String message;
}
