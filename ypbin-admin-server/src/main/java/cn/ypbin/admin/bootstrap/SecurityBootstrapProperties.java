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
package cn.ypbin.admin.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 一次性平台管理员初始化配置。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Getter
@Setter
@ConfigurationProperties(prefix = SecurityBootstrapProperties.PREFIX)
public class SecurityBootstrapProperties {

    public static final String PREFIX = "ypbin.admin.bootstrap";

    private boolean enabled;
    private String username;
    private String password;
    private String realName = "平台管理员";
    private Long tenantId = 1L;
}
