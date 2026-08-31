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

import lombok.Getter;
import lombok.Setter;
/**
 * 个人信息修改请求。仅允许修改本人的展示类信息。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class ProfileUpdateReq {

    /** 真实姓名 */
    private String realName;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 性别：0 未知、1 男、2 女 */
    private Integer gender;
}
