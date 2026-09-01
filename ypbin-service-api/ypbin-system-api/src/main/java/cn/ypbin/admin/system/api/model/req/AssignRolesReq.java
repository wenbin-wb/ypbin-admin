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

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
/**
 * 分配用户角色请求（覆盖式重设）。
 *
 * @author wenbin
 * @since 2026-08-03
 */
@Getter
@Setter
public class AssignRolesReq {

    /** 角色 ID 集合（空列表表示清空全部角色） */
    @NotNull(message = "角色集合不能为空")
    private List<Long> roleIds;
}
