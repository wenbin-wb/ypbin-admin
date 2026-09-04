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

/**
 * 公告投递记录。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Getter
@Setter
@TableName("sys_notice_delivery")
public class SysNoticeDelivery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 公告 ID */
    private Long noticeId;

    /** 公告发布版本 */
    private Long publishVersion;

    /** 接收人用户 ID */
    private Long receiverUserId;

    /** 投递通道 */
    private String channel;

    /** 目标地址 */
    private String targetAddress;

    /** 投递状态 */
    private String deliveryStatus;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 错误信息 */
    private String errorMessage;

    /** 投递成功时间 */
    private LocalDateTime deliveredTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
