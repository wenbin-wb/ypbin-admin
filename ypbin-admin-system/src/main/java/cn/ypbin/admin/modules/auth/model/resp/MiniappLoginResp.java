/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.model.resp;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 小程序登录响应。
 *
 * <p><b>为什么除令牌外还要回带用户资料</b>：小程序端登录后需立即渲染昵称/头像并写入本地缓存，
 * 若只回令牌，端侧必须再发一次「取当前用户」请求，且在两次请求之间处于「已登录但无身份」的
 * 空窗态（缓存判空会误判为未登录而重复登录）。故登录一次即返回端侧所需的最小身份信息。</p>
 *
 * <p>字段名遵循既有约定：令牌一律 {@code accessToken}，不额外提供 {@code token} 别名——
 * 同一含义两个名字会诱导客户端各取其一，长期必然分叉。</p>
 *
 * @author wenbin
 * @since 2026-10-30
 */
@Getter
@Setter
public class MiniappLoginResp {

    /** 访问令牌（Sa-Token token 值） */
    private String accessToken;

    /** 用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 微信 OpenID */
    private String openid;

    /** 昵称（缺省时回落真实姓名） */
    private String nickname;

    /** 头像地址 */
    private String avatarUrl;
}
