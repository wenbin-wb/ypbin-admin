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
import lombok.Getter;
import lombok.Setter;

/**
 * 家庭菜单-餐厅菜品库实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("familymenu_dish")
public class FamilymenuDish extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 餐厅 ID */
    private Long roomId;

    /** 菜品名称 */
    private String name;

    /** 菜品分类：热菜/凉菜/汤羹/主食/甜点小吃 */
    private String category;

    /** 菜品图片 */
    private String imageUrl;

    /** 标签（逗号分隔） */
    private String tags;

    /** 参考费用 */
    private BigDecimal price;
}

