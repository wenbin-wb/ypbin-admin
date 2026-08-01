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
import lombok.Data;

/**
 * 字典类型新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Data
public class DictSaveReq {

    /** 字典名称 */
    @NotBlank(message = "字典名称不能为空")
    private String name;

    /** 字典编码 */
    @NotBlank(message = "字典编码不能为空")
    private String code;

    /** 状态：1 正常、0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
