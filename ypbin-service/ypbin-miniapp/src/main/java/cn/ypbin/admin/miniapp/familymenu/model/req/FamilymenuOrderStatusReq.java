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
 * 订单就餐状态流转请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuOrderStatusReq {

    /** 订单 ID */
    @NotNull(message = "订单 ID 不能为空")
    private Long id;

    /** 目标状态：PICKING/WAITING/COOKING/DONE */
    @NotBlank(message = "订单状态不能为空")
    private String orderStatus;

    /** 采购费用 */
    private BigDecimal cost;

    /** 采购备注 */
    private String remark;

    /** 采购照片凭证（多张逗号分隔） */
    private String photo;

    /** 烹饪备注 */
    private String cookingRemark;

    /** 烹饪成品照片（多张逗号分隔） */
    private String cookingPhoto;
}
