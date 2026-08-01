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
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户。
 *
 * <p>租户表本身是全局资源，不参与租户隔离（配置在 ypbin.tenant.ignore-tables），故继承 {@link BaseEntity}。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户名称 */
    private String name;

    /** 租户编码（唯一） */
    private String code;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 到期时间，为空表示永不过期 */
    private LocalDate expireDate;

    /** 备注 */
    private String remark;
}
