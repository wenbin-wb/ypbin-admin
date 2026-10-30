/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.familymenu.model.req;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 开餐订单提交请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class FamilymenuOrderSubmitReq {

    /** 餐厅 ID */
    @NotNull(message = "餐厅 ID 不能为空")
    private Long roomId;

    /** 就餐日期（默认今天） */
    private LocalDate orderDate;

    /**
     * 就餐时段：BREAKFAST 早餐 LUNCH 午餐 DINNER 晚餐。
     *
     * <p>字段名与小程序的既有契约一致（端侧提交 {@code orderType}）。此前后端用 {@code mealTime}
     * 接收，端侧传的字段被静默忽略、一律落库为默认值 DINNER——午餐单会被记成晚餐单。</p>
     */
    private String orderType = "DINNER";
}
