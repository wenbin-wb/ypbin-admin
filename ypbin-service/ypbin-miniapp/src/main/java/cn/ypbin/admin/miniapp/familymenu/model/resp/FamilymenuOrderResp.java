/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 就餐订单响应。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuOrderResp {

    /** 订单 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 餐厅 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    /** 就餐日期 */
    private LocalDate orderDate;

    /** 就餐时段 */
    private String mealTime;

    /** 状态：PICKING/WAITING/COOKING/DONE */
    private String orderStatus;

    /** 采购费用 */
    private BigDecimal cost;

    /** 采购备注 */
    private String remark;

    /** 采购照片凭证 */
    private String photo;

    /** 烹饪备注 */
    private String cookingRemark;

    /** 烹饪成品照片 */
    private String cookingPhoto;

    /** 菜品明细列表 */
    private List<FamilymenuOrderDetailResp> details;
}
