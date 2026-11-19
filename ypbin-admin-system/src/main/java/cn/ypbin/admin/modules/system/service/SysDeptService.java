/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import cn.ypbin.admin.modules.system.entity.SysDept;
import cn.ypbin.admin.modules.system.model.req.DeptSaveReq;
import cn.ypbin.admin.modules.system.model.resp.DeptResp;
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
     * 查询部门自身及其全部下级部门 ID。
     *
     * <p>用户列表按部门筛选时要求「含下级」，这里统一提供展开结果，避免各处各写一遍递归。</p>
     *
     * @param deptId 部门 ID
     * @return 部门 ID 集合（含自身）；部门不存在时返回空集合
     */
    List<Long> listSelfAndDescendantIds(Long deptId);

    /**
     * 新增部门。
     *
     * @param req 请求
     */
    /**
     * 新增部门。
     *
     * <p>返回新部门 ID：前端新建时要顺带绑定「空间」，没有 ID 就只能静默丢弃用户的选择。</p>
     *
     * @param req 请求
     * @return 新部门 ID
     */
    Long createDept(DeptSaveReq req);

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
