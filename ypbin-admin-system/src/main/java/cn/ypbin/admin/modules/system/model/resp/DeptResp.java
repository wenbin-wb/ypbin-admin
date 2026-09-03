/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.resp;

import cn.ypbin.starter.json.ref.RefText;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 部门树节点。用于 {@code /system/dept/list}。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeptResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long pid;

    private String name;

    private Integer sort;

    private String leader;

    private String phone;

    private String email;

    private Integer status;

    private String remark;

    @RefText("user")
    private Long createUser;

    private LocalDateTime createTime;

    private List<DeptResp> children;
}
