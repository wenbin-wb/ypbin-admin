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
 * 牌账清-房间状态。
 *
 * <p><b>为什么 code 是 String 而非 int</b>：本状态的落库列为 {@code cardtab_room.room_status}
 * VARCHAR(16)，且已作为对外 JSON 契约（见 {@code CardtabRoomDetailResp.roomStatus}）被客户端消费。
 * 改用 int 需要一次 DB 迁移并破坏既有接口契约，收益不抵风险；故沿用字符串 code，但集中到本枚举，
 * 业务判断一律 {@code CardtabRoomStatusEnum.ONGOING.getCode().equals(status)}，禁止再散落裸字符串。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum CardtabRoomStatusEnum {

    /** 进行中 */
    ONGOING("ONGOING", "进行中"),

    /** 已结算 */
    SETTLED("SETTLED", "已结算"),

    /** 已关闭 */
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String desc;

    CardtabRoomStatusEnum(String code, String desc) {
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

    /**
     * 按编码取展示文案，未匹配（含 {@code null}）时归为未知。
     *
     * @param code 状态码
     * @return 展示文案
     */
    public static String descOf(String code) {
        for (CardtabRoomStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item.desc;
            }
        }
        return "未知";
    }
}
