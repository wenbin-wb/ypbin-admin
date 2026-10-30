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
 * 家庭菜单-家庭成员角色。
 *
 * <p>权限判定（主厨专属操作）与落库/接口均以本枚举 code 为准，禁止散落 {@code "CHEF"} 裸字符串。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum FamilymenuMemberRoleEnum {

    /** 主厨 */
    CHEF("CHEF", "主厨"),

    /** 食客 */
    DINER("DINER", "食客");

    private final String code;
    private final String desc;

    FamilymenuMemberRoleEnum(String code, String desc) {
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
     * 按编码解析角色（忽略大小写）；非法值抛异常而非静默放过。
     *
     * @param code 角色码
     * @return 匹配的角色
     * @throws IllegalArgumentException 编码为空或不匹配任何角色时
     */
    public static FamilymenuMemberRoleEnum of(String code) {
        if (code != null) {
            for (FamilymenuMemberRoleEnum item : values()) {
                if (item.code.equalsIgnoreCase(code.trim())) {
                    return item;
                }
            }
        }
        throw new IllegalArgumentException("非法成员角色：" + code);
    }
}
