/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import cn.ypbin.starter.json.ref.RefText;
import java.time.LocalDateTime;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 岗位响应。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Data
public class PostResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String code;

    private String category;

    private Integer sort;

    private Integer status;

    private String remark;

    @RefText("user")
    private Long createUser;

    private LocalDateTime createTime;
}
