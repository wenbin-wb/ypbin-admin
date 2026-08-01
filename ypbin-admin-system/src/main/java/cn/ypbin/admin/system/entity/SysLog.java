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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统日志。承载操作日志与登录日志（按 module 区分），字段与 starter LogRecord 对齐。全局表。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_log")
public class SysLog extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日志描述 */
    private String description;

    /** 所属模块 */
    private String module;

    /** 请求方法（GET/POST...） */
    private String requestMethod;

    /** 请求 URI */
    private String requestUri;

    /** 请求参数 */
    private String requestParam;

    /** 请求体 */
    private String requestBody;

    /** 响应体 */
    private String responseBody;

    /** HTTP 状态码 */
    private Integer statusCode;

    /** 客户端 IP */
    private String ip;

    /** IP 归属地 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 登录客户端 ID */
    private String clientId;

    /** 客户端类型 */
    private String clientType;

    /** 认证方式 */
    private String authType;

    /** 操作人用户 ID */
    private Long operateUserId;

    /** 操作时间 */
    private LocalDateTime operateTime;

    /** 耗时（毫秒） */
    private Long timeTaken;

    /** 是否成功 */
    private Integer success;

    /** 错误信息 */
    private String errorMsg;
}
