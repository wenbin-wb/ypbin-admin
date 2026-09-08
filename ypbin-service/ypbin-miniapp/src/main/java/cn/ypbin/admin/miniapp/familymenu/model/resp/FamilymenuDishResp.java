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
import lombok.Getter;
import lombok.Setter;

/**
 * 餐厅菜品响应。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuDishResp {

    /** 菜品 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 餐厅 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    /** 菜品名称 */
    private String name;

    /** 分类 */
    private String category;

    /** 图片 */
    private String imageUrl;

    /** 标签 */
    private String tags;

    /** 参考费用 */
    private BigDecimal price;
}
