/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.model.req.MenuSaveReq;
import cn.ypbin.admin.system.model.resp.MenuResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 菜单服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysMenuService extends BaseService<SysMenu> {

    /**
     * 构建指定用户可见的路由树（供 {@code /menu/all}）。
     * 排除按钮类型，平台用户可见平台菜单。
     *
     * @param userId 用户 ID
     * @return 路由树
     */
    List<RouteResp> buildRoutes(Long userId);

    /**
     * 构建完整、有序的菜单管理列表（供 {@code /system/menu/list}），含禁用和按钮菜单。
     *
     * @return 菜单管理列表
     */
    List<MenuResp> tree();

    /**
     * 校验菜单名称是否已存在。
     *
     * @param name      菜单名称
     * @param excludeId 排除的菜单 ID（编辑时排除自身），可为 null
     * @return 是否已存在
     */
    boolean isNameExists(String name, Long excludeId);

    /**
     * 校验路由路径是否已存在。
     *
     * @param path      路由路径
     * @param excludeId 排除的菜单 ID，可为 null
     * @return 是否已存在
     */
    boolean isPathExists(String path, Long excludeId);

    /**
     * 新增菜单（名称查重）。
     *
     * @param req 请求
     */
    void createMenu(MenuSaveReq req);

    /**
     * 编辑菜单（名称查重排除自身）。
     *
     * @param id  菜单 ID
     * @param req 请求
     */
    void updateMenu(Long id, MenuSaveReq req);

    /**
     * 删除菜单（存在子菜单时拒绝，并清理角色-菜单关联）。
     *
     * @param id 菜单 ID
     */
    void deleteMenu(Long id);
}
