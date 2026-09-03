/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
/**
 * 系统参数新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class ConfigSaveReq {

    /** 参数分组 */
    @NotBlank(message = "参数分组不能为空")
    private String configGroup;

    /** 参数名称 */
    @NotBlank(message = "参数名称不能为空")
    private String name;

    /** 参数键 */
    @NotBlank(message = "参数键不能为空")
    private String configKey;

    /** 参数值 */
    private String configValue;

    /** 备注 */
    private String remark;
}
