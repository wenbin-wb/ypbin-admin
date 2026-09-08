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
 * 家庭菜单-家庭餐厅实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("familymenu_room")
public class FamilymenuRoom extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建人/主厨用户 ID */
    private Long ownerUserId;

    /** 餐厅/家庭名称 */
    private String name;

    /** 邀请码 */
    private String inviteCode;
}

