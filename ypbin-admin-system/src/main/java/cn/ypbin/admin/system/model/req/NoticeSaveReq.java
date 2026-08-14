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

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;
import cn.ypbin.starter.sensitivewords.annotation.SensitiveWordFilter;

/**
 * 公告保存请求。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Data
public class NoticeSaveReq {

    @SensitiveWordFilter
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 255, message = "公告标题不能超过 255 个字符")
    private String title;

    @SensitiveWordFilter
    @NotBlank(message = "公告内容不能为空")
    private String content;

    @Size(max = 512, message = "封面地址不能超过 512 个字符")
    private String cover;

    @NotNull(message = "公告类型不能为空")
    @Min(value = 1, message = "公告类型不合法")
    @Max(value = 2, message = "公告类型不合法")
    private Integer noticeType;

    @NotNull(message = "通知范围不能为空")
    @Min(value = 1, message = "通知范围不合法")
    @Max(value = 4, message = "通知范围不合法")
    private Integer noticeScope;

    @Size(max = 1024, message = "范围目标不能超过 1024 个字符")
    private String scopeTargetIds;

    @NotBlank(message = "通知方式不能为空")
    @Size(max = 64, message = "通知方式不能超过 64 个字符")
    private String notifyMethods;

    @NotNull(message = "置顶状态不能为空")
    @Min(value = 0, message = "置顶状态不合法")
    @Max(value = 1, message = "置顶状态不合法")
    private Integer isTop;

    @NotNull(message = "发布方式不能为空")
    @Min(value = 1, message = "发布方式不合法")
    @Max(value = 2, message = "发布方式不合法")
    private Integer publishType;

    @Min(value = 0, message = "发布状态不合法")
    @Max(value = 0, message = "发布状态不合法")
    private Integer publishStatus;

    private LocalDateTime scheduledTime;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    @AssertTrue(message = "生效时间必须早于失效时间")
    public boolean isEffectivePeriodValid() {
        return effectiveTime == null || expireTime == null || effectiveTime.isBefore(expireTime);
    }
}
