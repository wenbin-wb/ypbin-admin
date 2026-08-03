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
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 路由节点：顶层为路由字段（name/path/component/redirect/children），展示相关字段收进嵌套 {@link Meta}。
 *
 * <p>由扁平的 {@code SysMenu} 组装而来（派生视图，不改字段名）。为空的字段不序列化，保持响应干净。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteResp {

    /** 路由名称 */
    private String name;

    /** 路由路径 */
    private String path;

    /** 组件路径（目录用 BasicLayout） */
    private String component;

    /** 重定向 */
    private String redirect;

    /** 路由元信息 */
    private Meta meta;

    /** 子路由 */
    private List<RouteResp> children;

    public void addChild(RouteResp child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }

    /**
     * 路由元信息。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta {

        /** 菜单标题 */
        private String title;

        /** 菜单图标 */
        private String icon;

        /** 激活图标 */
        private String activeIcon;

        /** 排序 */
        private Integer order;

        /** 是否缓存 */
        private Boolean keepAlive;

        /** 是否在菜单中隐藏 */
        private Boolean hideInMenu;

        /** 内嵌 iframe 地址 */
        private String iframeSrc;

        /** 外链地址 */
        private String link;

        /** 高亮的菜单路径 */
        private String activePath;

        /** 是否固定标签页 */
        private Boolean affixTab;

        /** 徽标内容 */
        private String badge;

        /** 徽标类型：dot/normal */
        private String badgeType;

        /** 徽标样式 */
        private String badgeVariants;

        /** 是否隐藏子菜单 */
        private Boolean hideChildrenInMenu;

        /** 是否在面包屑中隐藏 */
        private Boolean hideInBreadcrumb;

        /** 是否在标签栏中隐藏 */
        private Boolean hideInTab;
    }
}
