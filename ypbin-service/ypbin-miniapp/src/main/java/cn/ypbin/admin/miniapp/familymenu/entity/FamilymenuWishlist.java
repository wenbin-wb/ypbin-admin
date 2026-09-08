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
 * 家庭菜单-心愿单实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("familymenu_wishlist")
public class FamilymenuWishlist extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 餐厅 ID */
    private Long roomId;

    /** 许愿用户 ID */
    private Long userId;

    /** 心愿菜品名 */
    private String dishName;

    /** 心愿状态：PENDING 待接单 ACCEPTED 已接单 REJECTED 已婉拒 */
    private String wishStatus;

    /** 备注说明 */
    private String remark;
}

