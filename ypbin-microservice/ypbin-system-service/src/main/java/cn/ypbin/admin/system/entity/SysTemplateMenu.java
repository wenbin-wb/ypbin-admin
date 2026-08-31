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

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 权限模板-菜单关联。纯关联表，仅承载两个外键。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_template_menu")
public class SysTemplateMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限模板 ID */
    private Long templateId;

    /** 菜单 ID */
    private Long menuId;
}
