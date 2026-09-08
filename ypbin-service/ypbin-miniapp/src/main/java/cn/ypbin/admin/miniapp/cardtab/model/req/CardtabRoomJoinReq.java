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
 * 加入房间请求。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabRoomJoinReq {

    /** 加入类型：PASSCODE / QRCODE / INVITE */
    private String joinType;

    /** 房间口令/房间号 */
    private String roomCode;

    /** 邀请 Token（可选） */
    private String inviteToken;

    /** 昵称（可选） */
    private String nickname;
}
