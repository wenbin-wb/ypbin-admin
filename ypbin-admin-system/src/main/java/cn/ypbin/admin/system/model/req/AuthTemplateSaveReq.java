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
import lombok.Getter;
import lombok.Setter;
/**
 * 权限模板新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
public class AuthTemplateSaveReq {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    private String name;

    /** 模板编码 */
    @NotBlank(message = "模板编码不能为空")
    private String code;

    /** 备注 */
    private String remark;

    /** 授权的菜单 ID 集合 */
    private List<Long> menuIds;
}
