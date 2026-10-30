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
 * 牌账清-意见反馈处理状态。
 *
 * @author wenbin
 * @since 2026-10-16
 */
public enum CardtabFeedbackStatusEnum {

    /** 待处理 */
    PENDING("PENDING", "待处理"),

    /** 已处理 */
    PROCESSED("PROCESSED", "已处理");

    private final String code;
    private final String desc;

    CardtabFeedbackStatusEnum(String code, String desc) {
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
