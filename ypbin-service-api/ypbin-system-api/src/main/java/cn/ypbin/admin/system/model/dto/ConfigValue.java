/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 系统参数取值（内部 Feign 契约）。
 *
 * <p>auth 等服务读取系统参数（登录开关、短信/邮件模板等）时经内部端点获取，
 * 键不存在时返回默认值由调用方决定。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Getter
@Setter
public class ConfigValue {

    /** 参数键 */
    private String configKey;

    /** 参数值（未配置时为空串） */
    private String configValue;
}
