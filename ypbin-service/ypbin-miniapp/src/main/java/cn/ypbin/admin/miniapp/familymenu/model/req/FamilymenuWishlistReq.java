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
 * 心愿单提交请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuWishlistReq {

    /** 餐厅 ID */
    @NotNull(message = "餐厅 ID 不能为空")
    private Long roomId;

    /** 心愿菜品名称 */
    @NotBlank(message = "菜品名称不能为空")
    private String dishName;

    /** 备注 */
    private String remark;
}
