/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service.impl;

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.admin.ai.entity.AiChatRole;
import cn.ypbin.admin.ai.entity.AiChatRoleFavorite;
import cn.ypbin.admin.ai.mapper.AiChatRoleFavoriteMapper;
import cn.ypbin.admin.ai.mapper.AiChatRoleMapper;
import cn.ypbin.admin.ai.model.req.AiChatRoleSaveReq;
import cn.ypbin.admin.ai.model.resp.AiChatRoleResp;
import cn.ypbin.admin.ai.service.AiChatRoleService;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 对话角色服务实现。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Service
@RequiredArgsConstructor
public class AiChatRoleServiceImpl implements AiChatRoleService {

    private final AiChatRoleMapper roleMapper;
    private final AiChatRoleFavoriteMapper favoriteMapper;

    @Override
    public List<AiChatRoleResp> listRoles() {
        Long userId = LoginHelper.getUserId();
        Long tenantId = currentTenantId();
        // 内置角色（tenant_id=0）与当前租户自定义角色，忽略租户拦截以读取内置角色
        List<AiChatRole> roles = TenantContext.executeIgnore(() ->
            roleMapper.selectList(
                new LambdaQueryWrapper<AiChatRole>()
                    .eq(AiChatRole::getStatus, EntityStatus.ENABLED.getCode())
                    .and(w -> w.eq(AiChatRole::getTenantId, 0)
                        .or().eq(AiChatRole::getTenantId, tenantId))
                    .orderByAsc(AiChatRole::getSort)));

        // 收藏表已含 tenant_id，由租户拦截器正常注入
        Set<Long> favSet = favoriteMapper.selectList(
            new LambdaQueryWrapper<AiChatRoleFavorite>()
                .eq(AiChatRoleFavorite::getUserId, userId)
                .select(AiChatRoleFavorite::getRoleId))
            .stream()
            .map(AiChatRoleFavorite::getRoleId)
            .collect(Collectors.toSet());

        return roles.stream().map(r -> {
            AiChatRoleResp resp = new AiChatRoleResp();
            BeanUtils.copyProperties(r, resp);
            resp.setIsFavorite(favSet.contains(r.getId()));
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(AiChatRoleSaveReq req) {
        AiChatRole role = new AiChatRole();
        BeanUtils.copyProperties(req, role);
        role.setIsBuiltin(0);
        role.setSort(100);
        role.setStatus(EntityStatus.ENABLED.getCode());
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, AiChatRoleSaveReq req) {
        AiChatRole role = requireCustomRole(id);
        BeanUtils.copyProperties(req, role);
        roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        requireCustomRole(id);
        roleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleFavorite(Long roleId) {
        Long userId = LoginHelper.getUserId();
        AiChatRoleFavorite existing = favoriteMapper.selectOne(
            new LambdaQueryWrapper<AiChatRoleFavorite>()
                .eq(AiChatRoleFavorite::getUserId, userId)
                .eq(AiChatRoleFavorite::getRoleId, roleId));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
        } else {
            AiChatRoleFavorite fav = new AiChatRoleFavorite();
            fav.setUserId(userId);
            fav.setRoleId(roleId);
            favoriteMapper.insert(fav);
        }
    }

    private AiChatRole requireCustomRole(Long id) {
        AiChatRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (role.getIsBuiltin() == 1) {
            throw new BusinessException("内置角色不可修改");
        }
        return role;
    }

    private Long currentTenantId() {
        return UserContext.getTenantId().orElse(0L);
    }
}