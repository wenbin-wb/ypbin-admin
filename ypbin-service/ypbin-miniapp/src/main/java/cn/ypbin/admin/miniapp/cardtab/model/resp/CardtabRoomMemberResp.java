/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 房间成员出参。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabRoomMemberResp {

    /** 成员主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 头像文字 */
    private String avatarText;

    /** 角色：OWNER/MEMBER */
    private String role;

    /** 状态：IN_ROOM/LEFT */
    private String memberStatus;

    /** 当前积分 */
    private BigDecimal totalScore;
}
