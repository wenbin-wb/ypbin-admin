/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 当前登录用户信息。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class UserInfoResp {

    /** 用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 真实姓名/显示名 */
    private String realName;

    /** 头像 */
    private String avatar;

    /** 用户描述 */
    private String desc;

    /** 首页地址 */
    private String homePath;

    /** 角色码集合（供权限判定与菜单路由） */
    private List<String> roles;

    /** 权限码集合 */
    private List<String> permissions;
}
