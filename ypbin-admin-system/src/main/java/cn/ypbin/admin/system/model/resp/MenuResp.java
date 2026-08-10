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
 * 菜单管理响应。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long pid;

    private List<MenuResp> children;

    private String name;

    private String type;

    private Boolean platformOnly;

    private String path;

    private String component;

    private String authCode;

    private String redirect;

    private String title;

    private String icon;

    private String activeIcon;

    private Integer sort;

    private Integer status;

    private Boolean keepAlive;

    private Boolean hideInMenu;

    private String iframeSrc;

    private String link;

    private String activePath;

    private Boolean affixTab;

    private String badge;

    private String badgeType;

    private String badgeVariants;

    private Boolean hideChildrenInMenu;

    private Boolean hideInBreadcrumb;

    private Boolean hideInTab;
}
