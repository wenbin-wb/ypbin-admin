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
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 家庭餐厅详情响应。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuRoomDetailResp {

    /** 餐厅 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 餐厅名称 */
    private String name;

    /** 邀请码 */
    private String inviteCode;

    /** 创建人/主厨 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUserId;

    /** 成员列表 */
    private List<FamilymenuMemberResp> members;

    /** 菜品数量 */
    private Integer dishCount;
}
