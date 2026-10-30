/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 意见反馈提交请求。
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Getter
@Setter
public class CardtabFeedbackReq {

    /** 反馈内容 */
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 1000, message = "反馈内容不能超过 1000 个字符")
    private String content;

    /** 联系方式（微信号等，便于回复） */
    @Size(max = 64, message = "联系方式不能超过 64 个字符")
    private String contact;
}
