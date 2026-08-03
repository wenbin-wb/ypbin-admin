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
import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.entity.SysRoleMenu;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.system.model.req.MenuSaveReq;
import cn.ypbin.admin.system.model.resp.MenuResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 菜单服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends BaseServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    /** 按钮类型：不进路由树，仅贡献权限码 */
    private static final String TYPE_BUTTON = "button";

    private final SysPermissionService permissionService;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysAuthTemplateService authTemplateService;

    @Override
    public List<RouteResp> buildRoutes(Long userId) {
        List<SysMenu> menus = permissionService.isSuperAdmin(userId)
            ? list(allMenusOrdered())
            : baseMapper.selectByUserId(userId);
        List<SysMenu> routable = menus.stream()
            .filter(m -> !TYPE_BUTTON.equals(m.getType()))
            .sorted(Comparator.comparing(m -> m.getSort() == null ? 0 : m.getSort()))
            .toList();
        return buildRouteTree(applyTenantMenuFilter(routable), AdminConstants.ROOT_PARENT_ID);
    }

    /**
     * 按租户权限模板过滤可见菜单，并保留其祖先节点以保证路由树完整。
     */
    private List<SysMenu> applyTenantMenuFilter(List<SysMenu> menus) {
        Long tenantId = UserContext.getLoginUser().map(u -> u.getTenantId()).orElse(null);
        Set<Long> allowedIds = authTemplateService.resolveTenantMenuIds(tenantId);
        if (allowedIds == null) {
            return menus;
        }
        // 建 id->menu 索引，便于向上回溯祖先
        Map<Long, SysMenu> byId = menus.stream()
            .collect(Collectors.toMap(SysMenu::getId, m -> m, (a, b) -> a));
        Set<Long> keptIds = new HashSet<>();
        for (SysMenu menu : menus) {
            if (!allowedIds.contains(menu.getId())) {
                continue;
            }
            // 保留该菜单及其所有祖先（沿 pid 一路向上，到 pid=0 为止）
            Long cur = menu.getId();
            while (cur != null && cur != 0L && keptIds.add(cur)) {
                SysMenu node = byId.get(cur);
                cur = (node == null) ? null : node.getPid();
            }
        }
        return menus.stream().filter(m -> keptIds.contains(m.getId())).toList();
    }

    @Override
    public List<MenuResp> tree() {
        List<SysMenu> menus = list(allMenusOrdered());
        return buildMenuTree(menus, AdminConstants.ROOT_PARENT_ID);
    }

    @Override
    public boolean isNameExists(String name, Long excludeId) {
        return exists(new LambdaQueryWrapper<SysMenu>()
            .eq(SysMenu::getName, name)
            .ne(excludeId != null, SysMenu::getId, excludeId));
    }

    @Override
    public boolean isPathExists(String path, Long excludeId) {
        return exists(new LambdaQueryWrapper<SysMenu>()
            .eq(SysMenu::getPath, path)
            .ne(excludeId != null, SysMenu::getId, excludeId));
    }

    @Override
    public void createMenu(MenuSaveReq req) {
        if (isNameExists(req.getName(), null)) {
            throw new BusinessException("菜单名称已存在：" + req.getName());
        }
        SysMenu menu = new SysMenu();
        BeanUtils.copyProperties(req, menu);
        if (menu.getPid() == null) {
            menu.setPid(AdminConstants.ROOT_PARENT_ID);
        }
        save(menu);
    }

    @Override
    public void updateMenu(Long id, MenuSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("菜单不存在");
        }
        if (isNameExists(req.getName(), id)) {
            throw new BusinessException("菜单名称已存在：" + req.getName());
        }
        SysMenu menu = new SysMenu();
        BeanUtils.copyProperties(req, menu);
        menu.setId(id);
        if (menu.getPid() == null) {
            menu.setPid(AdminConstants.ROOT_PARENT_ID);
        }
        updateById(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(Long id) {
        boolean hasChildren = exists(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getPid, id));
        if (hasChildren) {
            throw new BusinessException("存在子菜单，不能删除");
        }
        removeById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }

    private LambdaQueryWrapper<SysMenu> allMenusOrdered() {
        return new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort);
    }

    /**
     * 递归组装前端路由树。
     */
    private List<RouteResp> buildRouteTree(List<SysMenu> menus, Long pid) {
        List<RouteResp> tree = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (pid.equals(menu.getPid())) {
                RouteResp route = toRoute(menu);
                List<RouteResp> children = buildRouteTree(menus, menu.getId());
                if (!children.isEmpty()) {
                    route.setChildren(children);
                }
                tree.add(route);
            }
        }
        return tree;
    }

    /**
     * 递归组装菜单管理树（含按钮）。
     */
    private List<MenuResp> buildMenuTree(List<SysMenu> menus, Long pid) {
        List<MenuResp> tree = new ArrayList<>();
        for (SysMenu menu : menus) {
            if (pid.equals(menu.getPid())) {
                MenuResp node = toMenuResp(menu);
                List<MenuResp> children = buildMenuTree(menus, menu.getId());
                if (!children.isEmpty()) {
                    node.setChildren(children);
                }
                tree.add(node);
            }
        }
        return tree;
    }

    private RouteResp toRoute(SysMenu menu) {
        RouteResp route = new RouteResp();
        route.setName(menu.getName());
        route.setPath(menu.getPath());
        route.setComponent(menu.getComponent());
        route.setRedirect(menu.getRedirect());
        route.setMeta(toMeta(menu));
        return route;
    }

    private MenuResp toMenuResp(SysMenu menu) {
        MenuResp node = new MenuResp();
        node.setId(menu.getId());
        node.setPid(menu.getPid());
        node.setName(menu.getName());
        node.setType(menu.getType());
        node.setPath(menu.getPath());
        node.setComponent(menu.getComponent());
        node.setAuthCode(menu.getAuthCode());
        node.setRedirect(menu.getRedirect());
        node.setStatus(menu.getStatus());
        node.setMeta(toMeta(menu));
        return node;
    }

    private RouteResp.Meta toMeta(SysMenu menu) {
        RouteResp.Meta meta = new RouteResp.Meta();
        meta.setTitle(menu.getTitle());
        meta.setIcon(menu.getIcon());
        meta.setActiveIcon(menu.getActiveIcon());
        meta.setOrder(menu.getSort());
        meta.setKeepAlive(menu.getKeepAlive());
        meta.setHideInMenu(menu.getHideInMenu());
        meta.setIframeSrc(menu.getIframeSrc());
        meta.setLink(menu.getLink());
        meta.setActivePath(menu.getActivePath());
        meta.setAffixTab(menu.getAffixTab());
        meta.setBadge(menu.getBadge());
        meta.setBadgeType(menu.getBadgeType());
        meta.setBadgeVariants(menu.getBadgeVariants());
        meta.setHideChildrenInMenu(menu.getHideChildrenInMenu());
        meta.setHideInBreadcrumb(menu.getHideInBreadcrumb());
        meta.setHideInTab(menu.getHideInTab());
        return meta;
    }
}
