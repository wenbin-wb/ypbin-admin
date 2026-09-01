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
import lombok.Getter;
import lombok.Setter;
/**
 * 部门新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
public class DeptSaveReq {

    /** 父部门 ID，顶级为 0 */
    private Long pid;

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    private String name;

    /** 显示排序 */
    private Integer sort;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：1 正常、0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
