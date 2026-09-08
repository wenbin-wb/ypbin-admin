/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 家庭菜单-就餐订单实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("familymenu_order")
public class FamilymenuOrder extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 餐厅 ID */
    private Long roomId;

    /** 就餐日期 */
    private LocalDate orderDate;

    /** 就餐时段：BREAKFAST 早餐 LUNCH 午餐 DINNER 晚餐 */
    private String mealTime;

    /** 状态：PICKING 点餐中 WAITING 等待做饭 COOKING 烹饪中 DONE 已开饭 */
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

