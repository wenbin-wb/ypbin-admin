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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 牌账清-房间成员实体。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
@TableName("cardtab_room_member")
public class CardtabRoomMember extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 房间 ID */
    private Long roomId;

    /** 用户 ID */
    private Long userId;

    /** 房间内昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatarUrl;

    /** 头像文字 */
    private String avatarText;

    /** 角色：OWNER 房主 MEMBER 成员 */
    private String role;

    /** 成员状态：IN_ROOM 在房 LEFT 已退出 */
    private String memberStatus;

    /** 当前累计总分 */
    private BigDecimal totalScore;

    /** 加入时间 */
    private LocalDateTime joinedAt;

    /** 退出时间 */
    private LocalDateTime leftAt;
}

