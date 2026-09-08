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
import lombok.Getter;
import lombok.Setter;

/**
 * 点菜明细提交请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuOrderDetailReq {

    /** 订单 ID */
    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    /** 菜品 ID（可选） */
    private Long dishId;

    /** 菜品名称 */
    @NotBlank(message = "菜品名称不能为空")
    private String dishName;

    /** 菜品图片 */
    private String dishImage;

    /** 份数 */
    private Integer quantity = 1;
}
