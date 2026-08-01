/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.entity;

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号（租户内唯一） */
    private String username;

    /** 登录密码（BCrypt 密文，响应不输出） */
    private String password;

    /** 真实姓名/显示名 */
    private String realName;

    /** 昵称 */
    private String nickname;

    /** 所属部门 ID */
    private Long deptId;

    /** 头像 URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别：0 未知、1 男、2 女 */
    private Integer gender;

    /** 备注 */
    private String remark;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后修改密码时间（用于密码有效期判定） */
    private LocalDateTime pwdResetTime;
}
