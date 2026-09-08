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
 * 家庭菜单-餐厅成员实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("familymenu_member")
public class FamilymenuMember extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 餐厅 ID */
    private Long roomId;

    /** 用户 ID */
    private Long userId;

    /** 成员称呼 */
    private String nickname;

    /** 成员头像 */
    private String avatarUrl;

    /** 角色：CHEF 主厨 DINER 食客 */
    private String role;
}

