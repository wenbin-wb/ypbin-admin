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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 写一条普通站内信的请求（供业务域投递站内通知用）。
 *
 * <h2>为什么显式带 {@code tenantId}</h2>
 *
 * <p>本端点走**内部调用**（服务间 Feign），调用方是业务服务而非登录用户，
 * 请求上没有网关身份头 ⇒ {@code UserContext} 取不到租户。故由调用方显式声明租户，
 * 服务端进入该租户上下文写入。</p>
 *
 * <p><b>信任模型</b>：内部端点由 {@code X-Internal-Token} 守卫（见
 * {@code ypbin.security.internal-token}），只有持凭证的兄弟服务能调；因此
 * 「调用方声明的 tenantId」按受信值处理，但仍会校验该租户下**收件人真实存在**——
 * 否则站内信会写进库而收件人永远看不到（投递记录却标成成功），属静默失效。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
@Getter
@Setter
public class InboxMessageSendReq {

    /** 目标租户 ID（调用方显式声明，服务端据此进入租户上下文） */
    @NotNull(message = "租户 ID 不能为空")
    private Long tenantId;

    /** 收件人用户 ID（必须是该租户下真实存在的用户） */
    @NotNull(message = "收件人不能为空")
    private Long receiverUserId;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = MAX_TITLE_LENGTH, message = "标题最长 200 字符")
    private String title;

    /** 正文 */
    @NotBlank(message = "正文不能为空")
    @Size(max = MAX_CONTENT_LENGTH, message = "正文最长 4000 字符")
    private String content;

    /** 标题最大长度（与 {@code sys_message.title} 列一致） */
    public static final int MAX_TITLE_LENGTH = 200;

    /** 正文最大长度（与 {@code sys_message.content} 列一致） */
    public static final int MAX_CONTENT_LENGTH = 4000;
}
