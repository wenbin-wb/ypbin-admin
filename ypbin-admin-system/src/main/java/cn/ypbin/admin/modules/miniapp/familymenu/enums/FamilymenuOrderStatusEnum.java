/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.familymenu.enums;

/**
 * 家庭菜单-就餐订单状态。
 *
 * <p><b>取值来源</b>：{@code ORDERING/SHOPPING/COOKING/DONE} 是与小程序端既有的状态机契约
 * （端侧按此渲染「点单中 → 买菜中 → 烹饪中 → 已上桌」四步进度条，并据此决定按钮可用性），
 * 不是可以随手改名的内部枚举。历史上建表脚本曾用 {@code PICKING/WAITING} 表达前两步，
 * 与端侧对不上——端侧把它们当作未知状态，进度条与按钮会全部落到兜底分支。
 * 故此处统一为端侧契约值，建表脚本同步（见部署说明的迁移提示）。</p>
 *
 * <p>服务端目前只做落库，不做流转合法性之外的语义校验；若后续要加流转校验，
 * 应基于本枚举补齐合法的状态迁移表，而不是在各处散落字符串比较。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum FamilymenuOrderStatusEnum {

    /** 点单中（主厨已开启今日点菜） */
    ORDERING("ORDERING", "点单中"),

    /** 买菜中（主厨已出发采购） */
    SHOPPING("SHOPPING", "买菜中"),

    /** 烹饪中 */
    COOKING("COOKING", "烹饪中"),

    /** 已上桌 */
    DONE("DONE", "已上桌");

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
