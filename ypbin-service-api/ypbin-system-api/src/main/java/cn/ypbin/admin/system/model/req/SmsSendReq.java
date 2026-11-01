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
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 短信发送请求（内部通道）。
 *
 * <p>短信厂商配置（accessKey/secret/签名）只存在于 system-svc：sms4j 在 starter 中是可选依赖，
 * 且 {@code sys_config} 的敏感项经 {@code ISystemClient#getConfigByKey} 读取时是脱敏值。
 * 因此无短信厂商依赖的服务（auth）必须经本请求让 system 代发，不能自行调 {@code SmsUtils}。</p>
 *
 * @author wenbin
 * @since 2026-10-31
 */
@Getter
@Setter
public class SmsSendReq {

    /** 接收手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 模板 ID */
    @NotBlank(message = "短信模板不能为空")
    private String templateId;

    /** 模板变量 */
    private Map<String, String> variables;
}
