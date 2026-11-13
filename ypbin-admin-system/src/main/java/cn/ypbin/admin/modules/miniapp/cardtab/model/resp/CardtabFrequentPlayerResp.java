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

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 常用玩家出参：按与我同房次数排序的对家。
 *
 * <p>由服务端聚合，避免前端为了算「常用玩家」拉取全部历史房间与成员明细。</p>
 *
 * @author wenbin
 * @since 2026-11-13
 */
@Getter
@Setter
public class CardtabFrequentPlayerResp {

    /** 对家的用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 最近一次出现时的昵称（房间内昵称，可能被改过） */
    private String nickname;

    /** 头像地址 */
    private String avatarUrl;

    /** 头像文字（昵称末字），头像加载失败时兜底 */
    private String avatarText;

    /** 同房次数 */
    private Integer count;
}
