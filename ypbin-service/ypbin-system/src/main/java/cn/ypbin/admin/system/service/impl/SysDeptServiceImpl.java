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
import cn.ypbin.admin.system.entity.SysRoleDept;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.model.req.DeptSaveReq;
import cn.ypbin.admin.system.model.resp.DeptResp;
import cn.ypbin.admin.system.service.SysDeptService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SysDeptServiceImpl extends BaseServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    private final SysUserMapper userMapper;
    private final SysRoleDeptMapper roleDeptMapper;

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
        // 删除前校验部门下仍有用户引用，避免用户落入无部门状态
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getDeptId, id)) > 0) {
            throw new BusinessException("该部门下仍有用户，不能删除");
        }
        // 清理角色-部门数据权限引用
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>()
            .eq(SysRoleDept::getDeptId, id));
        if (!removeById(id)) {
            throw new BusinessException("删除部门失败");
        }
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
