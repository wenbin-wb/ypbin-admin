/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.enums;

/**
 * 公告发布状态。
 *
 * <p>数据库与接口存/传 {@code code}；业务判断一律经 {@link #getCode()} 与库值比较，
 * 禁止在服务与 Mapper SQL 里散落裸数字（此前三处各写一份 0/2/3，改一处漏两处）。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
public enum NoticePublishStatusEnum {

    /** 草稿 */
    DRAFT(0, "草稿"),

    /** 待发布（定时公告） */
    PENDING(1, "待发布"),

    /** 已发布 */
    PUBLISHED(2, "已发布"),

    /** 已撤回 */
    REVOKED(3, "已撤回");

    private final Integer code;
    private final String desc;

    NoticePublishStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
