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
 * 牌账清-房间流水事件类型。
 *
 * <p>与建表脚本 {@code cardtab_room_event.event_type} 注释中的取值一一对应，禁止散落裸字符串。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum CardtabEventTypeEnum {

    /** 创建房间 */
    ROOM_CREATED("ROOM_CREATED", "创建房间"),

    /** 成员加入 */
    MEMBER_JOINED("MEMBER_JOINED", "成员加入"),

    /** 成员退出 */
    MEMBER_LEFT("MEMBER_LEFT", "成员退出"),

    /** 新增支出 */
    EXPENSE_CREATED("EXPENSE_CREATED", "新增支出"),

    /** 撤销支出 */
    EXPENSE_REVOKED("EXPENSE_REVOKED", "撤销支出"),

    /** 房间结算 */
    ROOM_SETTLED("ROOM_SETTLED", "房间结算");

    private final String code;
    private final String desc;

    CardtabEventTypeEnum(String code, String desc) {
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
