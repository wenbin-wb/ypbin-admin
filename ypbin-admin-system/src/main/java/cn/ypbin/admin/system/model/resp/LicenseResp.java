/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import cn.ypbin.starter.json.ref.RefText;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 商业授权列表/详情响应。
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Data
public class LicenseResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 授权编号 */
    private String licenseId;

    /** 被授权方名称 */
    private String subject;

    /** 供应方备注 */
    private String remark;

    /** 允许运行的机器指纹列表 */
    private List<String> fingerprints;

    /** 绑定租户标识 */
    private String tenantId;

    /** 生效时间 */
    private LocalDateTime effectiveAt;

    /** 到期时间 */
    private LocalDateTime expireAt;

    /** 过期后的宽限天数 */
    private Integer graceDays;

    /** 授权的功能模块标识集合 */
    private List<String> modules;

    /** 业务额度限制 */
    private Map<String, Long> quotas;

    /** 自定义扩展参数 */
    private Map<String, String> attributes;

    /** 交付模式：CODE / FILE */
    private String deliveryMode;

    /** 签发来源：manual 手工 / payment 支付（预留） */
    private String source;

    /** 审批状态：DRAFT / PENDING / ISSUED / REJECTED / REVOKED */
    private String approveStatus;

    /** 审批人 */
    @JsonSerialize(using = ToStringSerializer.class)
    @RefText("user")
    private Long approveUser;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 驳回原因 */
    private String rejectReason;

    /**
     * 当前授权运行态：合法可用 / 非法可用（宽限期）/ 非法不可用。
     *
     * <p>由后端按到期/宽限期与当前时间实时计算，仅对已签发的授权有意义；未签发时为空。</p>
     */
    private String currentStatus;

    @JsonSerialize(using = ToStringSerializer.class)
    @RefText("user")
    private Long createUser;

    private LocalDateTime createTime;
}
