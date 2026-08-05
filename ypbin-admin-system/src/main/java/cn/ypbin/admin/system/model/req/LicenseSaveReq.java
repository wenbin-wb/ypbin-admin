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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 商业授权草稿新增/编辑请求。
 *
 * <p>仅承载授权的业务内容，签发产物（授权编号、authCode）与审批字段由后端在流转中生成，不接收入参。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Data
public class LicenseSaveReq {

    /** 被授权方名称 */
    @NotBlank(message = "被授权方不能为空")
    private String subject;

    /** 供应方备注 */
    private String remark;

    /** 允许运行的机器指纹列表（多机器绑定；为空表示不限机器） */
    private List<String> fingerprints;

    /** 绑定租户标识（为空表示不限租户） */
    private String tenantId;

    /** 生效时间（早于此时间视为未生效） */
    private LocalDateTime effectiveAt;

    /** 到期时间（为空表示永久授权） */
    private LocalDateTime expireAt;

    /** 过期后的宽限天数 */
    private Integer graceDays;

    /** 授权的功能模块标识集合（为空表示不做模块级限制） */
    private List<String> modules;

    /** 业务额度限制（如 device=100、user=500） */
    private Map<String, Long> quotas;

    /** 自定义扩展参数 */
    private Map<String, String> attributes;

    /** 交付模式：CODE 内联授权码 / FILE 授权文件 */
    @NotBlank(message = "交付模式不能为空")
    private String deliveryMode;
}
