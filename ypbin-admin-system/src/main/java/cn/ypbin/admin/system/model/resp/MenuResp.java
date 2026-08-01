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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
import lombok.Data;

/**
 * 菜单管理树节点：扁平字段 + 嵌套 meta + children，保留 id/pid/type/authCode 供管理页编辑。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long pid;

    private String name;

    private String type;

    private String path;

    private String component;

    private String authCode;

    private String redirect;

    private Integer status;

    private RouteResp.Meta meta;

    private List<MenuResp> children;
}
