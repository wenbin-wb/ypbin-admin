/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
/**
 * 用户新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class UserSaveReq {

    /** 登录账号 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码（新增必填；编辑留空表示不改密） */
    private String password;

    /** 真实姓名 */
    @NotBlank(message = "姓名不能为空")
    private String realName;

    /** 昵称 */
    private String nickname;

    /** 所属部门 ID */
    private Long deptId;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别：0 未知、1 男、2 女 */
    private Integer gender;

    /** 状态：1 正常、0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 分配的角色 ID 集合 */
    private List<Long> roleIds;

    /** 分配的岗位 ID 集合 */
    private List<Long> postIds;
}
