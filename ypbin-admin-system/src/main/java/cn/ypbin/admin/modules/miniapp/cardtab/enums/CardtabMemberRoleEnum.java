/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.enums;

/**
 * 牌账清-房间成员角色。
 *
 * <p>权限判定（房主专属操作）与落库/接口均以本枚举 code 为准，禁止散落 {@code "OWNER"} 裸字符串。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum CardtabMemberRoleEnum {

    /** 房主 */
    OWNER("OWNER", "房主"),

    /** 普通成员 */
    MEMBER("MEMBER", "成员");

    private final String code;
    private final String desc;

    CardtabMemberRoleEnum(String code, String desc) {
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
     * 判断给定角色码是否为本枚举项。
     *
     * @param code 角色码，可空
     * @return 相等时为 true
     */
    public boolean is(String code) {
        return this.code.equals(code);
    }
}
