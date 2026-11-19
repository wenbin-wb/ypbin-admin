/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.resp;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台账号列表出参。
 *
 * <p>只暴露平台账号管理需要的字段：平台账号不参与租户的部门/岗位/角色分配，
 * 因此不返回这些关联信息。</p>
 *
 * @author wenbin
 * @since 2026-11-17
 */
@Getter
@Setter
public class PlatformUserResp {

    private Long id;

    private String username;

    private String realName;

    private String nickname;

    /** 状态 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;
}
