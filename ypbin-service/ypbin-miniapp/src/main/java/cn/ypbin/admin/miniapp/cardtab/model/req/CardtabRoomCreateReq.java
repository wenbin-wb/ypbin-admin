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

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建房间请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabRoomCreateReq {

    /** 房间名称 */
    @NotBlank(message = "房间名称不能为空")
    private String name;

    /** 玩法类型 */
    private String gameType = "MAHJONG";

    /** 计分单位 */
    private String unit = "积分";

    /** 记账模式 */
    private String bookkeepingMode = "SELF_PAY_TO_MEMBER";

    /** 是否允许口令加入 */
    private Boolean allowPasscodeJoin = true;
}
