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
import lombok.Getter;
import lombok.Setter;

/**
 * 内部密码校验请求。
 *
 * <p>明文密码必须走请求体：URL 查询串会进入网关/代理 access log、链路追踪与异常日志，
 * 与「敏感字段不得出现在日志」冲突。</p>
 *
 * @author wenbin
 * @since 2026-11-01
 */
@Getter
@Setter
public class PasswordVerifyReq {

    /** 用户 ID */
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /** 待校验明文密码 */
    @NotBlank(message = "密码不能为空")
    private String rawPassword;
}
