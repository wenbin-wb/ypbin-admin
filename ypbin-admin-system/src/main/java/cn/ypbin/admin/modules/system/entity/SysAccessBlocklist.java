/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 访问黑名单规则。
 *
 * <p>平台级全局表（不隔离租户）：恶意 IP 不应随租户切换而失效。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
@Getter
@Setter
@TableName("sys_access_blocklist")
public class SysAccessBlocklist extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 匹配维度：IP/CIDR/USER_AGENT/PATH */
    private String matchType;

    /** 匹配值；语义由 matchType 决定 */
    private String matchValue;

    /** 命中原因 */
    private String reason;

    /** 到期时间；为空表示长期有效 */
    private LocalDateTime expireTime;
}
