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
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 房间详情出参。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabRoomDetailResp {

    /** 房间 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 房间名称 */
    private String name;

    /** 房间口令 */
    private String roomCode;

    /** 玩法类型 */
    private String gameType;

    /** 计分单位 */
    private String unit;

    /** 记账方式 */
    private String bookkeepingMode;

    /** 是否允许口令加入 */
    private Boolean allowPasscodeJoin;

    /** 房间状态：ONGOING/SETTLED/CLOSED */
    private String roomStatus;

    /** 房主用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUserId;

    /** 房间人数 */
    private Integer memberCount;

    /** 房间成员列表 */
    private List<CardtabRoomMemberResp> members;

    /** 最近流水事件 */
    private List<CardtabEventResp> recentEvents;

    /** 创建时间 */
    private LocalDateTime createTime;
}
