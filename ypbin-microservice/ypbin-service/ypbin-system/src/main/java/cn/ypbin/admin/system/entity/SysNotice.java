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

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户公告。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_notice")
public class SysNotice extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 标题 */
    private String title;

    /** 公告内容（富文本） */
    private String content;

    /** 封面图 URL（可选） */
    private String cover;

    /** 公告类型：1 通知、2 公告 */
    private Integer noticeType;

    /** 通知范围：1 全体、2 指定角色、3 指定部门、4 指定用户 */
    private Integer noticeScope;

    /** 范围目标 ID 集合（逗号分隔，范围非全体时生效） */
    private String scopeTargetIds;

    /** 通知方式（逗号分隔：site 站内信 / email 邮件 / sms 短信） */
    private String notifyMethods;

    /** 是否置顶：1 是、0 否 */
    private Integer isTop;

    /** 发布方式：1 立即、2 定时 */
    private Integer publishType;

    /** 发布状态：0 草稿、1 待发布、2 已发布、3 已撤回 */
    private Integer publishStatus;

    /** 发布版本 */
    private Long publishVersion;

    /** 定时发布时间（发布方式为定时时生效） */
    private LocalDateTime scheduledTime;

    /** 实际发布时间 */
    private LocalDateTime publishTime;

    /** 生效时间 */
    private LocalDateTime effectiveTime;

    /** 失效时间 */
    private LocalDateTime expireTime;
}
