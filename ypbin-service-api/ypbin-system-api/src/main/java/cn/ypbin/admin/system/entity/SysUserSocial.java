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

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户-第三方平台绑定。全局表，不隔离租户。
 *
 * <p>共享类：复制自单体版 ypbin-admin-system（与 auth-svc 同源），
 * 已归位至 api 模块，作为跨服务共享契约。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_user_social")
public class SysUserSocial extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 平台标识（github/gitee/qq/wechat_open/alipay/dingtalk） */
    private String platform;

    /** 第三方用户 openId */
    private String openId;

    /** 第三方用户 unionId（微信体系用） */
    private String unionId;

    /** 第三方昵称 */
    private String nickname;

    /** 第三方头像 */
    private String avatar;

    /** 第三方 accessToken */
    private String accessToken;
}
