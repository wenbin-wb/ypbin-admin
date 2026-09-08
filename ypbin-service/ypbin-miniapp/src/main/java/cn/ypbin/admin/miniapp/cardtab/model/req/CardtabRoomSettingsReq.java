/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.model.req;

import lombok.Getter;
import lombok.Setter;

/**
 * 房间配置修改请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabRoomSettingsReq {

    /** 房间名称 */
    private String name;

    /** 玩法类型 */
    private String gameType;

    /** 计分单位 */
    private String unit;

    /** 记账模式 */
    private String bookkeepingMode;

    /** 是否允许口令加入 */
    private Boolean allowPasscodeJoin;
}
