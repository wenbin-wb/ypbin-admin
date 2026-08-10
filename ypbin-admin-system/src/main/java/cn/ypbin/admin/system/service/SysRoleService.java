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

    PageResult<RoleResp> pageRoles(RoleQuery query);

    List<RoleResp> listAll();

    void createRole(RoleSaveReq req);

    void updateRole(Long id, RoleSaveReq req);

    void updateStatus(Long id, Integer status);

    void deleteRole(Long id);
}
