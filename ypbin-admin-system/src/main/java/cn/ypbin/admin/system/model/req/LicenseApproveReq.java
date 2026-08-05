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

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商业授权审批请求。
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Data
public class LicenseApproveReq {

    /** 是否通过：true 通过并签发、false 驳回 */
    @NotNull(message = "审批结论不能为空")
    private Boolean approve;

    /** 驳回原因（驳回时必填） */
    private String rejectReason;
}
