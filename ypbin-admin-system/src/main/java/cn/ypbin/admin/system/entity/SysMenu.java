/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统菜单/权限。
 *
 * <p>菜单是全局资源（不隔离租户），故继承 {@link BaseEntity}。{@code type} 区分目录、菜单、按钮、内嵌和外链，
 * 按钮类型不进路由树、仅贡献权限码。展示属性以扁平列存储，路由响应按需组装元信息。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父菜单 ID，顶级为 0 */
    private Long pid;

    /** 菜单名称（路由 name，全局唯一） */
    private String name;

    /** 菜单类型：catalog 目录、menu 菜单、button 按钮、embedded 内嵌、link 外链 */
    private String type;

    /** 是否仅平台用户可见 */
    private Boolean platformOnly;

    /** 路由路径 */
    private String path;

    /** 组件路径（目录使用布局占位） */
    private String component;

    /** 权限标识（按钮/接口鉴权用，如 system:user:add） */
    private String authCode;

    /** 重定向地址 */
    private String redirect;

    /** 菜单标题（支持 i18n key） */
    private String title;

    /** 菜单图标 */
    private String icon;

    /** 激活时显示的图标 */
    private String activeIcon;

    /** 显示排序（order 为 SQL 保留字，列名用 sort） */
    private Integer sort;

    /** 是否缓存页面 */
    private Boolean keepAlive;

    /** 是否在菜单中隐藏 */
    private Boolean hideInMenu;

    /** 内嵌 iframe 的 URL */
    private String iframeSrc;

    /** 外链地址 */
    private String link;

    /** 高亮的菜单路径（当前路由不在菜单中显示时，指定需要激活的菜单） */
    private String activePath;

    /** 是否固定标签页 */
    private Boolean affixTab;

    /** 徽标内容（badgeType 为 normal 时生效） */
    private String badge;

    /** 徽标类型：dot 点、normal 文字 */
    private String badgeType;

    /** 徽标样式：default/destructive/primary/success/warning */
    private String badgeVariants;

    /** 是否隐藏子菜单 */
    private Boolean hideChildrenInMenu;

    /** 是否在面包屑中隐藏 */
    private Boolean hideInBreadcrumb;

    /** 是否在标签栏中隐藏 */
    private Boolean hideInTab;
}
