/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录客户端。管理各类终端（Web/App/小程序）的认证策略。全局表，不隔离租户。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_client")
public class SysClient extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户端 ID（如 web-admin/app/miniapp） */
    private String clientId;

    /** 客户端密钥（可选，开放平台场景启用） */
    private String clientSecret;

    /** 客户端类型：WEB/APP/MINI/API */
    private String clientType;

    /** 支持的认证方式，逗号分隔（ACCOUNT,PHONE,EMAIL,SOCIAL） */
    private String authTypes;

    /** Token 有效期（秒） */
    private Long timeout;

    /** Token 活跃超时（秒） */
    private Long activeTimeout;

    /** 是否允许同账号多端登录 */
    private Integer concurrentEnabled;

    /** 同账号最大登录数，-1 不限制 */
    private Integer maxLoginCount;

    /** 备注 */
    private String remark;
}
