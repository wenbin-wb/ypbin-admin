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
import java.util.List;
import lombok.Data;

/**
 * 角色新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Data
public class RoleSaveReq {

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    private String name;

    /** 角色标识 */
    @NotBlank(message = "角色标识不能为空")
    private String code;

    /** 数据范围 */
    private Integer dataScope;

    /** 显示排序 */
    private Integer sort;

    /** 状态：1 正常、0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 分配的菜单 ID 集合（前端字段 permissions） */
    private List<Long> permissions;
}
