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

import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysRoleMenu;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.model.query.RoleQuery;
import cn.ypbin.admin.system.model.req.RoleSaveReq;
import cn.ypbin.admin.system.model.resp.RoleResp;
import cn.ypbin.admin.system.service.SysRoleService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
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

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public PageResult<RoleResp> pageRoles(RoleQuery query) {
        PageResult<SysRole> source = page(query, new LambdaQueryWrapper<SysRole>()
            .like(StringUtils.hasText(query.getName()), SysRole::getName, query.getName())
            .like(StringUtils.hasText(query.getCode()), SysRole::getCode, query.getCode())
            .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
            .orderByAsc(SysRole::getSort));
        List<RoleResp> items = source.getItems().stream().map(this::toRespWithMenus).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    public List<RoleResp> listAll() {
        return list(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSort))
            .stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleSaveReq req) {
        checkCodeUnique(req.getCode(), null);
        SysRole role = new SysRole();
        BeanUtils.copyProperties(req, role, "permissions");
        save(role);
        assignMenus(role.getId(), req.getPermissions());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleSaveReq req) {
        SysRole existing = getById(id);
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }
        checkCodeUnique(req.getCode(), id);
        SysRole role = new SysRole();
        BeanUtils.copyProperties(req, role, "permissions");
        role.setId(id);
        updateById(role);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        assignMenus(id, req.getPermissions());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        if (id == 1L) {
            throw new BusinessException("内置超级管理员角色不可删除");
        }
        removeById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getCode, code)
            .ne(excludeId != null, SysRole::getId, excludeId));
        if (exists) {
            throw new BusinessException("角色标识已存在：" + code);
        }
    }

    private void assignMenus(Long roleId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            roleMenuMapper.insert(new SysRoleMenu(roleId, menuId));
        }
    }

    private RoleResp toResp(SysRole role) {
        RoleResp resp = new RoleResp();
        BeanUtils.copyProperties(role, resp);
        return resp;
    }

    private RoleResp toRespWithMenus(SysRole role) {
        RoleResp resp = toResp(role);
        resp.setPermissions(roleMenuMapper.selectMenuIdsByRoleId(role.getId()));
        return resp;
    }
}
