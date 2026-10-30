/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.model.resp;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * 玩法模板出参。
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Getter
@Setter
public class CardtabGameTemplateResp {

    /** 模板 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模板名称 */
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
