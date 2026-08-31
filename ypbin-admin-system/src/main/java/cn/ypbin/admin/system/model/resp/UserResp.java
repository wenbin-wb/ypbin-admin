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

import cn.ypbin.starter.json.dict.DictText;
import cn.ypbin.starter.json.ref.RefText;
import cn.ypbin.starter.json.sensitive.Sensitive;
import cn.ypbin.starter.json.sensitive.SensitiveType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 用户列表/详情响应。密码字段不输出。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class UserResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String realName;

    private String nickname;

    @JsonSerialize(using = ToStringSerializer.class)
    @RefText("dept")
    private Long deptId;

    private String avatar;

    @Sensitive(SensitiveType.PHONE)
    private String phone;

    @Sensitive(SensitiveType.EMAIL)
    private String email;

    /** 性别，额外输出 genderText（字典 sys_gender） */
    @DictText("sys_gender")
    private Integer gender;

    /** 状态，额外输出 statusText（字典 sys_status） */
    @DictText("sys_status")
    private Integer status;

    private String remark;

    @RefText("user")
    private Long createUser;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    /** 已分配角色 ID 集合 */
    private List<Long> roleIds;

    /** 已分配岗位 ID 集合 */
    private List<Long> postIds;
}
