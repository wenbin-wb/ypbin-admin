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

import cn.ypbin.starter.json.dict.DictText;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 个人中心响应。本人查看本人信息，手机/邮箱不脱敏（供编辑表单原样回填），密码不输出。
 *
 * @author wenbin
 * @since 2026-08-03
 */
@Getter
@Setter
public class ProfileResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String realName;

    private String nickname;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptId;

    private String avatar;

    /** 手机号（不脱敏） */
    private String phone;

    /** 邮箱（不脱敏） */
    private String email;

    /** 性别，额外输出 genderText（字典 sys_gender） */
    @DictText("sys_gender")
    private Integer gender;

    private String remark;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后改密时间 */
    private LocalDateTime pwdResetTime;

    private LocalDateTime createTime;

    /** 已分配角色 ID 集合 */
    private List<Long> roleIds;

    /** 已分配岗位 ID 集合 */
    private List<Long> postIds;
}
