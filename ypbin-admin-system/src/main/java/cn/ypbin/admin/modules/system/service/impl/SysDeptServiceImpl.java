/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.common.support.TenantSupport;
import cn.ypbin.admin.modules.system.entity.SysDept;
import cn.ypbin.admin.modules.system.mapper.SysDeptMapper;
import cn.ypbin.admin.modules.system.model.req.DeptSaveReq;
import cn.ypbin.admin.modules.system.model.resp.DeptResp;
import cn.ypbin.admin.modules.system.service.SysDeptService;
import cn.ypbin.admin.modules.system.service.SysSpaceRelationService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.tree.TreeUtils;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Map;
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

    /** 空间关联的目标类型：部门。 */
    private static final String SPACE_TARGET_DEPT = "dept";

    /** 部门树最大深度（与空间树同口径）。 */
    private static final int MAX_DEPT_DEPTH = 10;

    /** 部门删除时清理空间关联，避免留下指向已删部门的孤儿关联。 */
    private final SysSpaceRelationService spaceRelationService;

    @Override
    public List<DeptResp> tree() {
        List<SysDept> depts = list(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSort));
        // 用 starter 的 O(n) 函数式建树：手写递归在每层都全量扫描入参列表（O(n²)）；
        // SysDept 的字段名是 pid、实现不了 starter 的 TreeNode 接口（要求 getParentId()），
        // 故用函数式重载，不必为「适配接口」在实体上加方法。
        List<DeptResp> tree = depts.stream().map(this::toResp).toList();
        List<DeptResp> roots = TreeUtils.build(tree, DeptResp::getId, DeptResp::getPid,
            DeptResp::setChildren, AdminConstants.ROOT_PARENT_ID);
        // 与迁移前逐条对齐：原递归只在有子节点时 setChildren，叶子节点的 children 保持 null。
        // TreeUtils 一律写回（可能为空的）列表 ⇒ 叶子会多出 "children":[]；而 DeptResp 带
        // @JsonInclude(NON_NULL)——它只过滤 null、不过滤空集合 ⇒ 部门接口对外响应形态会被改变。
        // 故必须遍历全部节点（只遍历 roots 会漏掉深层叶子）把空集合归一为 null。
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
        validateParent(null, dept.getPid());
        if (!save(dept)) {
            throw new BusinessException("部门创建失败");
        }
        // 返回新 ID：前端据此绑定「空间」，否则新建时选的空间会被静默丢弃
        return dept.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(Long id, DeptSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("部门不存在");
        }
        SysDept dept = new SysDept();
        BeanUtils.copyProperties(req, dept);
        dept.setId(id);
        if (dept.getPid() == null) {
            dept.setPid(AdminConstants.ROOT_PARENT_ID);
        }
        // 与空间树同口径：父不能是自己、不能是自己的后代（成环）、不能超过深度上限
        validateParent(id, dept.getPid());
        if (!updateById(dept)) {
            throw new BusinessException("部门更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long id) {
        boolean hasChildren = exists(new LambdaQueryWrapper<SysDept>().eq(SysDept::getPid, id));
        if (hasChildren) {
            throw new BusinessException("存在子部门，不能删除");
        }
        removeById(id);
        // 空间是多态关联：部门删除后清理其空间归属，避免关联表指向已删部门
        spaceRelationService.removeByTarget(SPACE_TARGET_DEPT, id);
    }

    private DeptResp toResp(SysDept dept) {
        DeptResp resp = new DeptResp();
        BeanUtils.copyProperties(dept, resp);
        return resp;
    }

    /**
     * 校验父部门合法：存在、非自身、非自身后代、深度不超限。
     *
     * <p>只取「父 → 根」这一条父链（递归 CTE，一次查询），不再把整张部门表拉进内存。
     * 取不到租户时显式失败，不允许退化成跨租户查询。空 pid / 根 pid 直接通过。</p>
     *
     * @param selfId 当前部门 ID（新建时为 null）
     * @param pid    目标父部门 ID
     */
    private void validateParent(Long selfId, Long pid) {
        long parentId = pid == null ? AdminConstants.ROOT_PARENT_ID : pid;
        if (parentId == AdminConstants.ROOT_PARENT_ID) {
            return;
        }
        if (selfId != null && selfId == parentId) {
            throw new BusinessException("父部门不能是自己");
        }
        Long tenantId = TenantSupport.currentTenantId();
        List<Map<String, Object>> chain = baseMapper.selectParentChain(
            parentId, tenantId, MAX_DEPT_DEPTH + 1);
        if (chain.isEmpty()) {
            throw new BusinessException("父部门不存在：" + parentId);
        }
        for (Map<String, Object> row : chain) {
            long nodeId = asLong(row.get("id"));
            if (selfId != null && selfId == nodeId) {
                throw new BusinessException("部门不能挂到自己的后代下（父链上出现了当前部门本身）");
            }
        }
        // 父链自下而上：父部门若在第 N 层，新部门就落在第 N + 1 层
        int newDepth = chain.size() + 1;
        if (newDepth > MAX_DEPT_DEPTH) {
            throw new BusinessException("部门层级不能超过 " + MAX_DEPT_DEPTH
                + " 层（当前将达第 " + newDepth + " 层）");
        }
        // 移动的是整棵子树：只校验自身新深度拦不住「拖着深子树往下挪」
        if (selfId != null) {
            Integer maxRelDepth = baseMapper.selectSubtreeMaxRelDepth(
                selfId, tenantId, MAX_DEPT_DEPTH + 1);
            int deepest = newDepth + (maxRelDepth == null ? 0 : maxRelDepth);
            if (deepest > MAX_DEPT_DEPTH) {
                throw new BusinessException("移动后该部门的子树将达第 " + deepest + " 层，超过上限 "
                    + MAX_DEPT_DEPTH + " 层：请先精简子树，或挂到更浅的位置");
            }
        }
        // 链尾必须是根（pid=0）；否则说明父链断裂（父部门被删/跨租户）或数据成环
        Map<String, Object> tail = chain.get(chain.size() - 1);
        if (asLong(tail.get("pid")) != AdminConstants.ROOT_PARENT_ID) {
            throw new BusinessException("部门层级数据异常（父链断裂）：deptId="
                + asLong(tail.get("id")));
        }
    }

    /** CTE 查询结果是 Map，JDBC 可能返回 Integer/Long，统一按 long 取值（null 视为根）。 */
    private static long asLong(Object value) {
        if (value == null) {
            return AdminConstants.ROOT_PARENT_ID;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("部门层级数据异常：无法解析列值 " + value);
    }
}
