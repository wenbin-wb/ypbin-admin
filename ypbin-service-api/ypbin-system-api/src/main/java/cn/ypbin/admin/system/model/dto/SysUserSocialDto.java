/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.dto;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * 第三方账号绑定快照（跨服务只读视图）。
 *
 * <p>与 {@link SysUserDto} 同因：避免把继承 {@code BaseEntity} 的实体暴露给无数据源的 auth，
 * 字段名与实体逐一同名。<b>刻意不含 {@code accessToken}</b>——绑定时写入的第三方访问令牌
 * 不参与任何读取路径，不随缓存或跨服务视图扩散。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
@Getter
@Setter
public class SysUserSocialDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属用户 ID */
    private Long userId;

    /** 平台标识（github/gitee/wechat 等） */
    private String platform;

    /** 平台内用户唯一标识 */
    private String openId;
}
