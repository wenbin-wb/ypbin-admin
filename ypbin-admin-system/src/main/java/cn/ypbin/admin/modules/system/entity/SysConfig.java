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
import lombok.Getter;
import lombok.Setter;

/**
 * 系统参数配置。key-value 结构，按分组归类，全局共享（不隔离租户），故继承 {@link BaseEntity}。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 参数分组（如 site/password/login/mail/sms），列名 config_group 避开 SQL 保留字 group */
    private String configGroup;

    /** 参数名称 */
    private String name;

    /** 参数键（唯一） */
    private String configKey;

    /** 参数值 */
    private String configValue;

    /** 是否内置：1 内置不可删、0 可删（列 built_in 避开保留字 inner） */
    private Integer builtIn;

    /** 备注 */
    private String remark;
}
