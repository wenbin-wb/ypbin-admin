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

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.model.req.DeptSaveReq;
import cn.ypbin.admin.system.model.resp.DeptResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 部门服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysDeptService extends BaseService<SysDept> {

    /**
     * 部门树。
     *
     * @return 部门树
     */
    List<DeptResp> tree();

    /**
     * 新增部门。
     *
     * @param req 请求
     */
    void createDept(DeptSaveReq req);

    /**
     * 编辑部门。
     *
     * @param id  部门 ID
     * @param req 请求
     */
    void updateDept(Long id, DeptSaveReq req);

    /**
     * 删除部门（存在子部门时拒绝）。
     *
     * @param id 部门 ID
     */
    void deleteDept(Long id);
}
