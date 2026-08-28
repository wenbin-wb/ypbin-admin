/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.model.req.DeptSaveReq;
import cn.ypbin.admin.system.model.resp.DeptResp;
import cn.ypbin.admin.system.service.SysDeptService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部门服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
public class SysDeptServiceImpl extends BaseServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    @Override
    public List<DeptResp> tree() {
        List<SysDept> depts = list(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSort));
        return buildTree(depts, AdminConstants.ROOT_PARENT_ID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDept(DeptSaveReq req) {
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(req, dept);
        if (dept.getPid() == null) {
            dept.setPid(AdminConstants.ROOT_PARENT_ID);
        }
        save(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(Long id, DeptSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("部门不存在");
        }
        if (id.equals(req.getPid())) {
            throw new BusinessException("父部门不能是自己");
        }
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(req, dept);
        dept.setId(id);
        if (dept.getPid() == null) {
            dept.setPid(AdminConstants.ROOT_PARENT_ID);
        }
        updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long id) {
        boolean hasChildren = exists(new LambdaQueryWrapper<SysDept>().eq(SysDept::getPid, id));
        if (hasChildren) {
            throw new BusinessException("存在子部门，不能删除");
        }
        removeById(id);
    }

    private List<DeptResp> buildTree(List<SysDept> depts, Long pid) {
        List<DeptResp> tree = new ArrayList<>();
        for (SysDept dept : depts) {
            if (pid.equals(dept.getPid())) {
                DeptResp node = toResp(dept);
                List<DeptResp> children = buildTree(depts, dept.getId());
                if (!children.isEmpty()) {
                    node.setChildren(children);
                }
                tree.add(node);
            }
        }
        return tree;
    }

    private DeptResp toResp(SysDept dept) {
        DeptResp resp = new DeptResp();
        BeanUtils.copyProperties(dept, resp);
        return resp;
    }
}
