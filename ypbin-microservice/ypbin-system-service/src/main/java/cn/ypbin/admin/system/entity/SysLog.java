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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 系统日志。承载操作日志与登录日志（按 module 区分），字段与 starter LogRecord 对齐。
 *
 * <p>不继承 BaseEntity：日志由 @Async 异步线程落库，异步线程无 Sa-Token 上下文，
 * 若走 BaseEntity 的审计字段自动填充会因取当前登录人而抛 SaTokenContextException。
 * 日志自带 operateUserId/operateTime 记录操作人与时间，无需审计/逻辑删除字段。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_log")
public class SysLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，雪花算法生成 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

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
