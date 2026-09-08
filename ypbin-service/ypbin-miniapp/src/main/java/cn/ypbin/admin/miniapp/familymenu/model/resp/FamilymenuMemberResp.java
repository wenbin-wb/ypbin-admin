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
 * 餐厅成员响应。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuMemberResp {

    /** 成员主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 餐厅 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    /** 用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 称呼 */
    private String nickname;

    /** 头像 */
    private String avatarUrl;

    /** 角色：CHEF 主厨 DINER 食客 */
    private String role;
}
