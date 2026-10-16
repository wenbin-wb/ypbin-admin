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
 * 家庭菜单-就餐订单状态。
 *
 * <p>状态流转目前为客户端驱动（PICKING → WAITING → COOKING → DONE），服务端只做落库；
 * 若后续要加流转校验，应基于本枚举补齐合法迁移表，而不是在各处散落字符串比较。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum FamilymenuOrderStatusEnum {

    /** 点餐中 */
    PICKING("PICKING", "点餐中"),

    /** 等待做饭 */
    WAITING("WAITING", "等待做饭"),

    /** 烹饪中 */
    COOKING("COOKING", "烹饪中"),

    /** 已开饭 */
    DONE("DONE", "已开饭");

    private final String code;
    private final String desc;

    FamilymenuOrderStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 编码是否为合法订单状态。
     *
     * @param code 状态码
     * @return 合法时为 true
     */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        for (FamilymenuOrderStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
