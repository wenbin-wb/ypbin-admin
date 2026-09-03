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

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 岗位。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_post")
public class SysPost extends TenantBaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 岗位名称 */
    private String name;

    /** 岗位编码 */
    private String code;

    /** 岗位分类 */
    private String category;

    /** 排序 */
    private Integer sort;

    /** 备注 */
    private String remark;
}
