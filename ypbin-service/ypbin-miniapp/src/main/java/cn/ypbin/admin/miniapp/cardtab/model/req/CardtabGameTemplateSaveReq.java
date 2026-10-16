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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 玩法模板保存请求。
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Getter
@Setter
public class CardtabGameTemplateSaveReq {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 64, message = "模板名称不能超过 64 个字符")
    private String name;

    /** 玩法类型：MAHJONG/DOUDUIZHU/RUNFAST/CUSTOM */
    @NotBlank(message = "玩法类型不能为空")
    private String gameType;

    /** 计分单位 */
    @NotBlank(message = "计分单位不能为空")
    private String unit;

    /** 记账模式：SELF_PAY_TO_MEMBER/MULTI_MEMBER_RECORD */
    private String bookkeepingMode;

    /** 是否允许口令加入 */
    private Boolean allowPasscodeJoin;
}
