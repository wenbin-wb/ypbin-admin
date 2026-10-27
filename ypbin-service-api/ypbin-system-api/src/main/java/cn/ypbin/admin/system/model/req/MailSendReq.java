/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.model.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送一封纯文本邮件的请求（复用 system 侧既有的 JavaMail 能力）。
 *
 * <p><b>为什么不带 tenantId</b>：邮件投递不落库、不参与租户隔离，
 * 收件人就是一个邮箱地址。租户上下文对 SMTP 发送没有意义。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
@Getter
@Setter
public class MailSendReq {

    /** 收件人邮箱 */
    @NotBlank(message = "收件人不能为空")
    @Email(message = "收件人邮箱格式不合法")
    @Size(max = MAX_TO_LENGTH, message = "收件人邮箱过长")
    private String to;

    /** 主题 */
    @NotBlank(message = "主题不能为空")
    @Size(max = MAX_SUBJECT_LENGTH, message = "主题最长 200 字符")
    private String subject;

    /** 正文（纯文本） */
    @NotBlank(message = "正文不能为空")
    @Size(max = MAX_CONTENT_LENGTH, message = "正文最长 10000 字符")
    private String content;

    /** 收件人最大长度 */
    public static final int MAX_TO_LENGTH = 320;

    /** 主题最大长度 */
    public static final int MAX_SUBJECT_LENGTH = 200;

    /** 正文最大长度 */
    public static final int MAX_CONTENT_LENGTH = 10000;
}
