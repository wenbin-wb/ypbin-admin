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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 心愿单响应。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuWishlistResp {

    /** 心愿 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 餐厅 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    /** 许愿用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 心愿菜品名称 */
    private String dishName;

    /** 状态：PENDING/ACCEPTED/REJECTED */
    private String wishStatus;

    /** 备注说明 */
    private String remark;

    /** 许愿人昵称 */
    private String requesterName;

    /** 创建时间 */
    private LocalDateTime createTime;
}
