/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 联机校验对外接口配置。
 *
 * <p>消费端联机回验需访问本签发端提供的「按授权编号查询状态」接口，该接口不要求 Sa-Token 登录，
 * 以共享令牌（请求头 {@code X-License-Token}）轻量鉴权。令牌须与消费端 {@code ypbin.license.online.token}
 * 保持一致，经环境变量注入；未配置时联机校验一律拒绝（不静默放行）。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ypbin.license.remote")
public class LicenseRemoteProperties {

    /** 消费端联机校验共享令牌（请求头 X-License-Token）；为空则联机校验拒绝所有请求 */
    private String token;
}
