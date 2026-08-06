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
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 商业授权。平台级签发记录，全局表不隔离租户（授权本身可绑定某个业务租户，见 tenantId 字段）。
 *
 * <p>承载一条授权从草稿、提交、审批到签发、吊销的完整生命周期。标识/绑定/期限/范围类字段与
 * starter 的授权内容载体全程同名，签发时据此组装并经国密签名加密产出 authCode。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Getter
@Setter
@TableName(value = "sys_license", autoResultMap = true)
public class SysLicense extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权编号（签发时生成，全局唯一，用于联机校验与吊销） */
    private String licenseId;

    /** 被授权方名称 */
    private String subject;

    /** 供应方备注 */
    private String remark;

    /** 允许运行的机器指纹列表（多机器绑定；为空表示不限机器） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fingerprints;

    /** 绑定租户标识（为空表示不限租户） */
    private String tenantId;

    /** 生效时间（早于此时间视为未生效） */
    private LocalDateTime effectiveAt;

    /** 到期时间（为空表示永久授权） */
    private LocalDateTime expireAt;

    /** 过期后的宽限天数（此期间状态为非法可用） */
    private Integer graceDays;

    /** 授权的功能模块标识集合（为空表示不做模块级限制） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> modules;

    /** 业务额度限制（如 device=100、user=500） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Long> quotas;

    /** 自定义扩展参数 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> attributes;

    /** 交付模式：CODE 内联授权码 / FILE 授权文件 */
    private String deliveryMode;

    /** 签发来源：manual 手工 / payment 支付（预留，当前默认手工） */
    private String source;

    /** 签发产物：Base64 授权串（审批通过后写入） */
    private String authCode;

    /** 审批状态：DRAFT 草稿 / PENDING 待审批 / ISSUED 已签发 / REJECTED 已驳回 / REVOKED 已吊销 */
    private String approveStatus;

    /** 审批人（签发/驳回操作人，须不同于创建人） */
    private Long approveUser;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 驳回原因 */
    private String rejectReason;
}
