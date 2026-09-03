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

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.modules.system.entity.SysDept;
import cn.ypbin.admin.modules.system.entity.SysMenu;
import cn.ypbin.admin.modules.system.entity.SysRole;
import cn.ypbin.admin.modules.system.entity.SysRoleDept;
import cn.ypbin.admin.modules.system.entity.SysRoleMenu;
import cn.ypbin.admin.modules.system.entity.SysUserRole;
import cn.ypbin.admin.modules.system.mapper.SysDeptMapper;
import cn.ypbin.admin.modules.system.mapper.SysMenuMapper;
import cn.ypbin.admin.modules.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.modules.system.mapper.SysRoleMapper;
import cn.ypbin.admin.modules.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.modules.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.modules.system.model.query.RoleQuery;
import cn.ypbin.admin.modules.system.model.req.RoleSaveReq;
import cn.ypbin.admin.modules.system.model.resp.RoleResp;
import cn.ypbin.admin.modules.system.service.SysAuthTemplateService;
import cn.ypbin.admin.modules.system.service.SysRoleService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 角色服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends BaseServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private static final int SCOPE_CUSTOM = 5;

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysDeptMapper deptMapper;
    private final SysAuthTemplateService authTemplateService;

    @Override
    public PageResult<RoleResp> pageRoles(RoleQuery query) {
        PageResult<SysRole> source = page(query, new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleType, AdminConstants.ROLE_TYPE_TENANT)
            .like(StringUtils.hasText(query.getName()), SysRole::getName, query.getName())
            .like(StringUtils.hasText(query.getCode()), SysRole::getCode, query.getCode())
            .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
            .orderByAsc(SysRole::getSort));
        List<SysRole> items = source.getItems();
        // 批量预加载本页全部角色的菜单/部门映射，避免逐行 N+1
        Map<Long, List<Long>> menuMap = batchMenuIds(items);
        Map<Long, List<Long>> deptMap = batchDeptIds(items);
        List<RoleResp> respItems = items.stream()
            .map(role -> toRespWithMenus(role, menuMap.get(role.getId()), deptMap.get(role.getId())))
            .toList();
        return PageResult.of(respItems, source.getTotal(), source.getPage(), source.getPageSize());
    }

    /**
     * 批量查询本页角色的菜单 ID 映射。
     *
     * @param roles 角色列表
     * @return roleId -> menuId 列表
     */
    private Map<Long, List<Long>> batchMenuIds(List<SysRole> roles) {
        if (roles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        return roleMenuMapper.selectMenuIdsByRoleIds(roleIds).stream()
            .collect(Collectors.groupingBy(SysRoleMenu::getRoleId,
                Collectors.mapping(SysRoleMenu::getMenuId, Collectors.toList())));
    }

    /**
     * 批量查询本页角色的部门 ID 映射。
     *
     * @param roles 角色列表
     * @return roleId -> deptId 列表
     */
    private Map<Long, List<Long>> batchDeptIds(List<SysRole> roles) {
        if (roles.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        return roleDeptMapper.selectDeptIdsByRoleIds(roleIds).stream()
            .collect(Collectors.groupingBy(SysRoleDept::getRoleId,
                Collectors.mapping(SysRoleDept::getDeptId, Collectors.toList())));
    }

    @Override
    public List<RoleResp> listAll() {
        return list(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleType, AdminConstants.ROLE_TYPE_TENANT)
            .eq(SysRole::getStatus, EntityStatus.ENABLED.getCode())
            .orderByAsc(SysRole::getSort))
            .stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleSaveReq req) {
        checkReservedCode(req.getCode());
        checkCodeUnique(req.getCode(), null);
        validateMenus(req.getPermissions());
        validateDepartments(req.getDataScope(), req.getDeptIds());
        SysRole role = new SysRole();
        BeanUtils.copyProperties(req, role, "permissions", "deptIds");
        role.setRoleType(AdminConstants.ROLE_TYPE_TENANT);
        save(role);
        assignMenus(role.getId(), req.getPermissions());
        assignDepartments(role.getId(), req.getDeptIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleSaveReq req) {
        SysRole existing = getById(id);
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }
        checkTenantRole(existing);
        checkReservedCode(req.getCode());
        checkCodeUnique(req.getCode(), id);
        validateMenus(req.getPermissions());
        validateDepartments(req.getDataScope(), req.getDeptIds());
        SysRole role = new SysRole();
        BeanUtils.copyProperties(req, role, "permissions", "deptIds");
        role.setId(id);
        updateById(role);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, id));
        assignMenus(id, req.getPermissions());
        assignDepartments(id, req.getDeptIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        checkTenantRole(role);
        boolean updated = update(new SysRole(), new LambdaUpdateWrapper<SysRole>()
            .eq(SysRole::getId, id)
            .eq(SysRole::getRoleType, AdminConstants.ROLE_TYPE_TENANT)
            .set(SysRole::getStatus, status));
        if (!updated) {
            throw new BusinessException("角色状态更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        checkTenantRole(role);
        removeById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
    }

    private void checkReservedCode(String code) {
        if (AdminConstants.SUPER_ADMIN_ROLE.equalsIgnoreCase(code)) {
            throw new BusinessException("角色标识为系统保留值：" + code);
        }
    }

    private void checkTenantRole(SysRole role) {
        if (!AdminConstants.ROLE_TYPE_TENANT.equals(role.getRoleType())) {
            throw new BusinessException("平台角色不可通过角色管理修改");
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getCode, code)
            .ne(excludeId != null, SysRole::getId, excludeId));
        if (exists) {
            throw new BusinessException("角色标识已存在：" + code);
        }
    }

    private void validateMenus(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        Set<Long> requestedIds = new HashSet<>(menuIds);
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
            .in(SysMenu::getId, requestedIds)
            .eq(SysMenu::getStatus, EntityStatus.ENABLED.getCode()));
        Set<Long> existingIds = menus.stream().map(SysMenu::getId).collect(Collectors.toSet());
        if (!existingIds.equals(requestedIds)) {
            throw new BusinessException("角色授权包含不存在或已禁用的菜单");
        }
        Long tenantId = currentTenantId();
        Set<Long> allowedIds = authTemplateService.resolveTenantMenuIds(tenantId);
        if (!allowedIds.containsAll(requestedIds)) {
            throw new BusinessException("角色授权包含租户权限模板之外的菜单");
        }
    }

    private void validateDepartments(Integer dataScope, List<Long> deptIds) {
        if (!Integer.valueOf(SCOPE_CUSTOM).equals(dataScope)) {
            if (deptIds != null && !deptIds.isEmpty()) {
                throw new BusinessException("仅自定义数据范围可以分配部门");
            }
            return;
        }
        if (deptIds == null || deptIds.isEmpty()) {
            throw new BusinessException("自定义数据范围必须分配部门");
        }
        Set<Long> requestedIds = new HashSet<>(deptIds);
        List<SysDept> departments = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
            .in(SysDept::getId, requestedIds)
            .eq(SysDept::getStatus, EntityStatus.ENABLED.getCode()));
        Set<Long> existingIds = departments.stream().map(SysDept::getId).collect(Collectors.toSet());
        Long tenantId = currentTenantId();
        boolean invalid = !existingIds.equals(requestedIds)
            || departments.stream().anyMatch(dept -> !tenantId.equals(dept.getTenantId()));
        if (invalid) {
            throw new BusinessException("角色数据范围包含不存在、已禁用或跨租户部门");
        }
    }

    private void assignMenus(Long roleId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : new HashSet<>(menuIds)) {
            roleMenuMapper.insert(new SysRoleMenu(roleId, menuId));
        }
    }

    private void assignDepartments(Long roleId, List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return;
        }
        for (Long deptId : new HashSet<>(deptIds)) {
            roleDeptMapper.insert(new SysRoleDept(roleId, deptId));
        }
    }

    private Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法确定当前租户"));
    }

    private RoleResp toResp(SysRole role) {
        RoleResp resp = new RoleResp();
        BeanUtils.copyProperties(role, resp);
        return resp;
    }

    private RoleResp toRespWithMenus(SysRole role) {
        return toRespWithMenus(role,
            roleMenuMapper.selectMenuIdsByRoleId(role.getId()),
            roleDeptMapper.selectDeptIdsByRoleId(role.getId()));
    }

    private RoleResp toRespWithMenus(SysRole role, List<Long> menuIds, List<Long> deptIds) {
        RoleResp resp = toResp(role);
        resp.setPermissions(menuIds == null ? List.of() : menuIds);
        resp.setDeptIds(deptIds == null ? List.of() : deptIds);
        return resp;
    }
}
