/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 牌账清-玩法模板实体。
 *
 * <p>模板是「一套建房配置」的复用载体（玩法/单位/记账模式/是否允许口令加入），归属创建者本人，
 * 不属任何房间——故用 {@code owner_user_id} 表达归属，不做房间级归属校验。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Getter
@Setter
@TableName("cardtab_game_template")
public class CardtabGameTemplate extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 归属用户 ID（模板仅本人可见可改） */
    private Long ownerUserId;

    /** 模板名称 */
    private String name;

    /** 玩法类型：MAHJONG/DOUDUIZHU/RUNFAST/CUSTOM */
    private String gameType;

    /** 计分单位 */
    private String unit;

    /** 记账模式：SELF_PAY_TO_MEMBER/MULTI_MEMBER_RECORD */
    private String bookkeepingMode;

    /** 是否允许口令加入：1 允许 0 禁止 */
    private Integer allowPasscodeJoin;
}
