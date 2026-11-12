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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 菜单管理响应。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuResp {

    private Long id;

    private Long pid;

    private List<MenuResp> children;

    private String name;

    private String type;

    private Boolean platformOnly;

    /**
     * 是否可授予租户角色：仅 {@code /system/role/assignable-menus} 返回。
     * 仅作为祖先保留的节点为 false（前端置灰，避免提交后被模板校验拒绝）。
     */
    private Boolean assignable;

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
