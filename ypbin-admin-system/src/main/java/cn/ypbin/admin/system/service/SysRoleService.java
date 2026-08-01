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

import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.model.query.RoleQuery;
import cn.ypbin.admin.system.model.req.RoleSaveReq;
import cn.ypbin.admin.system.model.resp.RoleResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 角色服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysRoleService extends BaseService<SysRole> {

    /**
     * 分页查询角色列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<RoleResp> pageRoles(RoleQuery query);

    /**
     * 查询全部角色（下拉选项用）。
     *
     * @return 角色列表
     */
    List<RoleResp> listAll();

    /**
     * 新增角色（编码查重 + 事务内分配菜单）。
     *
     * @param req 请求
     */
    void createRole(RoleSaveReq req);

    /**
     * 编辑角色（编码查重排除自身 + 事务内重分配菜单）。
     *
     * @param id  角色 ID
     * @param req 请求
     */
    void updateRole(Long id, RoleSaveReq req);

    /**
     * 删除角色（同时清理角色-菜单与用户-角色关联）。
     *
     * @param id 角色 ID
     */
    void deleteRole(Long id);
}
