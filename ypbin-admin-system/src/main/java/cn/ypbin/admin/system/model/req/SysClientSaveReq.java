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
import lombok.Data;

/**
 * 登录客户端新增/编辑请求。clientSecret 由后端生成，不接收前端传入。
 *
 * @author wenbin
 * @since 2026-08-03
 */
@Data
public class SysClientSaveReq {

    /** 客户端 ID */
    @NotBlank(message = "客户端 ID 不能为空")
    private String clientId;

    /** 客户端类型：WEB/APP/MINI/API */
    @NotBlank(message = "客户端类型不能为空")
    private String clientType;

    /** 支持的认证方式，逗号分隔 */
    private String authTypes;

    /** Token 有效期（秒） */
    private Long timeout;

    /** Token 活跃超时（秒） */
    private Long activeTimeout;

    /** 是否允许同账号多端登录 */
    private Integer concurrentEnabled;

    /** 同账号最大登录数 */
    private Integer maxLoginCount;

    /** 备注 */
    private String remark;
}
