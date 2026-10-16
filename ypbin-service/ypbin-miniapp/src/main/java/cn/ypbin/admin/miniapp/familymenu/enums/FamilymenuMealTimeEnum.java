/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.enums;

/**
 * 家庭菜单-就餐时段。
 *
 * <p>与建表脚本 {@code familymenu_order.meal_time} 注释取值一一对应。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum FamilymenuMealTimeEnum {

    /** 早餐 */
    BREAKFAST("BREAKFAST", "早餐"),

    /** 午餐 */
    LUNCH("LUNCH", "午餐"),

    /** 晚餐 */
    DINNER("DINNER", "晚餐");

    private final String code;
    private final String desc;

    FamilymenuMealTimeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
