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
 * 牌账清-支出记录状态。
 *
 * <p>撤销判定（{@code REVOKED} 不可重复撤销）依赖本枚举，禁止散落裸字符串。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public enum CardtabExpenseStatusEnum {

    /** 正常 */
    NORMAL("NORMAL", "正常"),

    /** 已撤销 */
    REVOKED("REVOKED", "已撤销");

    private final String code;
    private final String desc;

    CardtabExpenseStatusEnum(String code, String desc) {
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
