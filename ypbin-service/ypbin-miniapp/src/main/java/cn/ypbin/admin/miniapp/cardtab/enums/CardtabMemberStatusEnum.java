/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.enums;

/**
 * 牌账清-房间成员状态。
 *
 * <p>与 {@link CardtabRoomStatusEnum} 同理，code 沿用落库/接口既有的字符串（VARCHAR 列 + 对外契约），
 * 仅做集中化以杜绝散落裸字符串。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum CardtabMemberStatusEnum {

    /** 在房 */
    IN_ROOM("IN_ROOM", "在房"),

    /** 已退出 */
    LEFT("LEFT", "已退出");

    private final String code;
    private final String desc;

    CardtabMemberStatusEnum(String code, String desc) {
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
     * 判断给定状态码是否为本枚举项。
     *
     * @param code 状态码，可空
     * @return 相等时为 true
     */
    public boolean is(String code) {
        return this.code.equals(code);
    }
}
