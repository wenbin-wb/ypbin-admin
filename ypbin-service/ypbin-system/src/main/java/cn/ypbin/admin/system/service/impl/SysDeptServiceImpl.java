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
import cn.ypbin.admin.system.service.SysSpaceRelationService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.tree.TreeUtils;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    /** 空间关联的对象类型码（字典 space_target_type 的项值，dept）。 */
    private static final String SPACE_TARGET_DEPT = "dept";

    private final SysUserMapper userMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysSpaceRelationService spaceRelationService;

    @Override
    public List<DeptResp> tree() {
        List<SysDept> depts = list(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSort));
        // 用 starter 的 O(n) 函数式建树（starter #106）：原先手写递归在每层都全量扫描入参列表（O(n²)），
        // 且 `SysDept` 的字段名是 `pid`、实现不了 starter 的 TreeNode 接口（要求 getParentId()）。
        // 函数式重载允许宿主保留自己的字段名——本仓因此不必为「适配接口」再加一个方法。
        List<DeptResp> tree = depts.stream().map(this::toResp).toList();
        List<DeptResp> roots = TreeUtils.build(tree, DeptResp::getId, DeptResp::getPid,
            DeptResp::setChildren, AdminConstants.ROOT_PARENT_ID);
        // 与迁移前逐条对齐：原递归只在**有子节点**时 setChildren，叶子节点的 children 保持 null。
        // TreeUtils 一律写回（可能为空的）列表 ⇒ 叶子会多出 "children":[]；而 DeptResp 带
        // @JsonInclude(NON_NULL)——它只过滤 null、**不过滤空集合** ⇒ 部门接口对外响应形态被改变
        // （实测：迁移后 [{"id":2,...,"children":[]}] vs 迁移前 [{"id":2,...}]）。
        // 菜单侧 SysMenuServiceImpl 已做同样归一，部门侧此前漏了（由 iot 仓反向发现）。
        tree.forEach(node -> {
            if (node.getChildren() != null && node.getChildren().isEmpty()) {
                node.setChildren(null);
            }
        });
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDept(DeptSaveReq req) {
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(req, dept);
        if (dept.getPid() == null) {
            dept.setPid(AdminConstants.ROOT_PARENT_ID);
        }
        save(dept);
        // 返回新 ID：前端据此绑定「所在空间」，否则新建时选的空间会被静默丢弃
        return dept.getId();
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
        // 清理「部门 ↔ 空间」关联：多态关联没有数据库外键，不清理就会留下指向已删部门的幽灵行，
        // 表现为空间详情里的关联数虚高、且该空间因「仍挂着对象」永远删不掉。
        // 必须在删部门**之后**清（先清则删部门失败时关联已丢），故放在 removeById 之后。
        if (!removeById(id)) {
            throw new BusinessException("删除部门失败");
        }
        spaceRelationService.removeByTarget(SPACE_TARGET_DEPT, id);
    }

    private DeptResp toResp(SysDept dept) {
        DeptResp resp = new DeptResp();
        BeanUtils.copyProperties(dept, resp);
        return resp;
    }
}
