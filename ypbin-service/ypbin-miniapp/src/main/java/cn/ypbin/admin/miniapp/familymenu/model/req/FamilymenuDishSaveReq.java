/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 餐厅菜品提交请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuDishSaveReq {

    /** 主键 ID（修改时传） */
    private Long id;

    /** 餐厅 ID */
    @NotNull(message = "餐厅 ID 不能为空")
    private Long roomId;

    /** 菜品名称 */
    @NotBlank(message = "菜品名称不能为空")
    private String name;

    /** 菜品分类：热菜/凉菜/汤羹/主食/甜点小吃 */
    @NotBlank(message = "菜品分类不能为空")
    private String category;

    /** 菜品图片 */
    private String imageUrl;

    /** 标签（逗号分隔） */
    private String tags;

    /** 参考费用 */
    private BigDecimal price;
}
