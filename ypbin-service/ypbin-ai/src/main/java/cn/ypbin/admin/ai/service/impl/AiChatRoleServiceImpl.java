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
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityContext;
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

    /** 内置角色标记：是 */
    private static final int BUILTIN_YES = 1;
    /** 内置角色标记：否 */
    private static final int BUILTIN_NO = 0;
    /** 用户自定义角色的默认排序值（内置角色占用更小值） */
    private static final int CUSTOM_ROLE_SORT = 100;

    private final AiChatRoleMapper roleMapper;
    private final AiChatRoleFavoriteMapper favoriteMapper;

    @Override
    public List<AiChatRoleResp> listRoles() {
        return listRoles(null);
    }

    @Override
    public List<AiChatRoleResp> listRoles(Integer status) {
        Long userId = IdentityContext.getUserId().orElse(null);
        Long tenantId = currentTenantId();
        // 内置角色（tenant_id=0）与当前租户自定义角色，忽略租户拦截以读取内置角色；
        // 状态：null=仅启用（默认行为），0/1 精确过滤供管理端找回已停用角色
        Integer filterStatus = status == null ? EntityStatus.ENABLED.getCode() : status;
        List<AiChatRole> roles = TenantContext.executeIgnore(() ->
            roleMapper.selectList(
                new LambdaQueryWrapper<AiChatRole>()
                    .eq(AiChatRole::getStatus, filterStatus)
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
        role.setTenantId(currentTenantId());
        role.setIsBuiltin(BUILTIN_NO);
        role.setSort(CUSTOM_ROLE_SORT);
        role.setStatus(EntityStatus.ENABLED.getCode());
        if (roleMapper.insert(role) != 1) {
            throw new BusinessException("角色创建失败");
        }
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, AiChatRoleSaveReq req) {
        AiChatRole role = requireCustomRole(id);
        BeanUtils.copyProperties(req, role);
        role.setIsBuiltin(BUILTIN_NO);
        if (roleMapper.updateById(role) != 1) {
            throw new BusinessException("角色更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        requireCustomRole(id);
        if (roleMapper.deleteById(id) != 1) {
            throw new BusinessException("角色删除失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleFavorite(Long roleId) {
        Long userId = IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
        // 收藏前必须确认角色可见：否则可以收藏任意租户的角色 ID，列表回显时泄露其存在性
        requireVisibleRole(roleId);
        AiChatRoleFavorite existing = favoriteMapper.selectOne(
            new LambdaQueryWrapper<AiChatRoleFavorite>()
                .eq(AiChatRoleFavorite::getUserId, userId)
                .eq(AiChatRoleFavorite::getRoleId, roleId));
        if (existing != null) {
            if (favoriteMapper.deleteById(existing.getId()) != 1) {
                throw new BusinessException("取消收藏失败");
            }
        } else {
            AiChatRoleFavorite fav = new AiChatRoleFavorite();
            fav.setUserId(userId);
            fav.setRoleId(roleId);
            if (favoriteMapper.insert(fav) != 1) {
                throw new BusinessException("收藏失败");
            }
        }
    }

    private AiChatRole requireCustomRole(Long id) {
        AiChatRole role = requireVisibleRole(id);
        if (role.getIsBuiltin() != null && role.getIsBuiltin() == BUILTIN_YES) {
            throw new BusinessException("内置角色不可修改");
        }
        return role;
    }

    /**
     * 取角色并校验可见性（内置角色或当前租户自定义角色）。
     *
     * @param id 角色 ID
     * @return 角色实体
     */
    private AiChatRole requireVisibleRole(Long id) {
        Long tenantId = currentTenantId();
        AiChatRole role = TenantContext.executeIgnore(() -> roleMapper.selectById(id));
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        boolean builtin = role.getIsBuiltin() != null && role.getIsBuiltin() == BUILTIN_YES;
        if (!builtin && !tenantId.equals(role.getTenantId())) {
            throw new BusinessException("无权访问该角色");
        }
        return role;
    }

    private Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}