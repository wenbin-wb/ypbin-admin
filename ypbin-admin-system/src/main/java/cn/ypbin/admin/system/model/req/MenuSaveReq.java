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
import lombok.Data;

/**
 * 菜单新增/编辑请求。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Data
public class MenuSaveReq {

    /** 父菜单 ID，顶级为 0 */
    private Long pid;

    /** 菜单名称（路由 name） */
    @NotBlank(message = "菜单名称不能为空")
    private String name;

    /** 菜单类型：catalog/menu/button/embedded/link */
    @NotBlank(message = "菜单类型不能为空")
    private String type;

    /** 路由路径 */
    private String path;

    /** 组件路径 */
    private String component;

    /** 权限标识 */
    private String authCode;

    /** 重定向 */
    private String redirect;

    /** 标题 */
    private String title;

    /** 图标 */
    private String icon;

    /** 激活图标 */
    private String activeIcon;

    /** 排序 */
    private Integer sort;

    /** 状态：1 正常、0 禁用 */
    private Integer status;

    /** 是否缓存 */
    private Boolean keepAlive;

    /** 是否隐藏 */
    private Boolean hideInMenu;

    /** 内嵌地址 */
    private String iframeSrc;

    /** 外链地址 */
    private String link;
}
