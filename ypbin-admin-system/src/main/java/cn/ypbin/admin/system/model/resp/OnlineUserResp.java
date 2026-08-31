/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 在线用户响应。在 starter {@code OnlineUser} 基础上补充用户真实姓名（按 userId 关联 sys_user）。
 *
 * @author wenbin
 * @since 2026-08-03
 */
@Getter
@Setter
public class OnlineUserResp {

    /** 用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 真实姓名（按 userId 关联用户表补充） */
    private String realName;

    /** 租户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    /** 会话 token */
    private String token;

    /** 登录客户端 ID */
    private String clientId;

    /** 设备类型 */
    private String deviceType;

    /** 客户端 IP */
    private String ip;

    /** IP 归属地 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 登录时间 */
    private LocalDateTime loginTime;
}
