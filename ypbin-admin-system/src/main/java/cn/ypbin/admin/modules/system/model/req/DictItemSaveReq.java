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
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
/**
 * 字典项新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class DictItemSaveReq {

    /** 所属字典 ID */
    @NotNull(message = "所属字典不能为空")
    private Long dictId;

    /** 字典项标签 */
    @NotBlank(message = "字典项标签不能为空")
    private String label;

    /** 字典项值 */
    @NotBlank(message = "字典项值不能为空")
    private String value;

    /** 展示颜色 */
    private String color;

    /** 显示排序 */
    private Integer sort;

    /** 状态：1 正常、0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
