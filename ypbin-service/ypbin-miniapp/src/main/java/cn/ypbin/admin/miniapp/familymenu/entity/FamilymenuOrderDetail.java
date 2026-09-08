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
import lombok.Getter;
import lombok.Setter;

/**
 * 家庭菜单-订单明细实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("familymenu_order_detail")
public class FamilymenuOrderDetail extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 菜品库菜品 ID */
    private Long dishId;

    /** 菜品名称 */
    private String dishName;

    /** 菜品图片 */
    private String dishImage;

    /** 份数 */
    private Integer quantity;

    /** 点菜人用户 ID */
    private Long requesterId;
}

