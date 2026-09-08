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
import lombok.Getter;
import lombok.Setter;

/**
 * 订单菜品明细响应。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuOrderDetailResp {

    /** 明细 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 订单 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 菜品 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dishId;

    /** 菜品名称 */
    private String dishName;

    /** 菜品图片 */
    private String dishImage;

    /** 份数 */
    private Integer quantity;

    /** 点菜人用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requesterId;

    /** 点菜人昵称 */
    private String requesterName;
}
