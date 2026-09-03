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
import cn.ypbin.admin.modules.system.entity.SysMenu;
import cn.ypbin.admin.modules.system.entity.SysRoleMenu;
import cn.ypbin.admin.modules.system.mapper.SysMenuMapper;
import cn.ypbin.admin.modules.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.modules.system.model.req.MenuSaveReq;
import cn.ypbin.admin.modules.system.model.resp.MenuResp;
import cn.ypbin.admin.modules.system.model.resp.RouteResp;
import cn.ypbin.admin.modules.system.service.SysAuthTemplateService;
import cn.ypbin.admin.modules.system.service.SysMenuService;
import cn.ypbin.admin.modules.system.service.SysPermissionService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.util.StringUtils;

/**
 * 菜单服务实现。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends BaseServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private static final String TYPE_CATALOG = "catalog";
    private static final String TYPE_MENU = "menu";
    private static final String TYPE_BUTTON = "button";
    private static final String TYPE_EMBEDDED = "embedded";
    private static final String TYPE_LINK = "link";

    private final SysPermissionService permissionService;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysAuthTemplateService authTemplateService;

    @Override
    public List<RouteResp> buildRoutes(Long userId) {
        boolean platformUser = permissionService.isPlatformUser(userId);
        List<SysMenu> menus = permissionService.isSuperAdmin(userId)
            ? list(enabledMenusOrdered())
            : baseMapper.selectByUserId(userId);
        List<SysMenu> routable = menus.stream()
            .filter(menu -> !TYPE_BUTTON.equals(menu.getType()))
            .filter(menu -> platformUser || !Boolean.TRUE.equals(menu.getPlatformOnly()))
            .sorted(Comparator.comparing((SysMenu menu) -> menu.getSort() == null ? 0 : menu.getSort())
                .thenComparing(SysMenu::getId))
            .toList();
        List<SysMenu> visible = platformUser ? routable : applyTenantMenuFilter(routable);
        return buildRouteTree(visible, AdminConstants.ROOT_PARENT_ID);
    }

    private List<SysMenu> applyTenantMenuFilter(List<SysMenu> menus) {
        Long tenantId = UserContext.getLoginUser().map(user -> user.getTenantId())
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
        Set<Long> allowedIds = authTemplateService.resolveTenantMenuIds(tenantId);
        Map<Long, SysMenu> byId = menus.stream()
            .collect(Collectors.toMap(SysMenu::getId, menu -> menu, (first, ignored) -> first));
        Set<Long> keptIds = new HashSet<>();
        for (SysMenu menu : menus) {
            if (!allowedIds.contains(menu.getId())) {
                continue;
            }
            Long currentId = menu.getId();
            while (currentId != null && !AdminConstants.ROOT_PARENT_ID.equals(currentId) && keptIds.add(currentId)) {
                SysMenu current = byId.get(currentId);
                currentId = current == null ? null : current.getPid();
            }
        }
        return menus.stream().filter(menu -> keptIds.contains(menu.getId())).toList();
    }

    @Override
    public List<MenuResp> tree() {
        List<SysMenu> menus = list(new LambdaQueryWrapper<SysMenu>()
            .orderByAsc(SysMenu::getSort)
            .orderByAsc(SysMenu::getId));
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
    @Transactional(rollbackFor = Exception.class)
    public void createMenu(MenuSaveReq req) {
        validateMenu(req, null);
        SysMenu menu = normalize(req);
        if (!save(menu)) {
            throw new BusinessException("新增菜单失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMenu(Long id, MenuSaveReq req) {
        SysMenu existing = getById(id);
        if (existing == null) {
            throw new BusinessException("菜单不存在");
        }
        validateMenu(req, id);
        SysMenu menu = normalize(req);
        menu.setId(id);
        boolean updated = update(menu, new LambdaUpdateWrapper<SysMenu>()
            .eq(SysMenu::getId, id)
            .set(SysMenu::getPid, menu.getPid())
            .set(SysMenu::getName, menu.getName())
            .set(SysMenu::getType, menu.getType())
            .set(SysMenu::getPlatformOnly, menu.getPlatformOnly())
            .set(SysMenu::getPath, menu.getPath())
            .set(SysMenu::getComponent, menu.getComponent())
            .set(SysMenu::getAuthCode, menu.getAuthCode())
            .set(SysMenu::getRedirect, menu.getRedirect())
            .set(SysMenu::getTitle, menu.getTitle())
            .set(SysMenu::getIcon, menu.getIcon())
            .set(SysMenu::getActiveIcon, menu.getActiveIcon())
            .set(SysMenu::getSort, menu.getSort())
            .set(SysMenu::getStatus, menu.getStatus())
            .set(SysMenu::getKeepAlive, menu.getKeepAlive())
            .set(SysMenu::getHideInMenu, menu.getHideInMenu())
            .set(SysMenu::getIframeSrc, menu.getIframeSrc())
            .set(SysMenu::getLink, menu.getLink())
            .set(SysMenu::getActivePath, menu.getActivePath())
            .set(SysMenu::getAffixTab, menu.getAffixTab())
            .set(SysMenu::getBadge, menu.getBadge())
            .set(SysMenu::getBadgeType, menu.getBadgeType())
            .set(SysMenu::getBadgeVariants, menu.getBadgeVariants())
            .set(SysMenu::getHideChildrenInMenu, menu.getHideChildrenInMenu())
            .set(SysMenu::getHideInBreadcrumb, menu.getHideInBreadcrumb())
            .set(SysMenu::getHideInTab, menu.getHideInTab()));
        if (!updated) {
            throw new BusinessException("修改菜单失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("菜单不存在");
        }
        boolean hasChildren = exists(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getPid, id));
        if (hasChildren) {
            throw new BusinessException("存在子菜单，不能删除");
        }
        if (!removeById(id)) {
            throw new BusinessException("删除菜单失败");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }

    private void validateMenu(MenuSaveReq req, Long excludeId) {
        if (req.getStatus() != null && req.getStatus() != 0 && req.getStatus() != 1) {
            throw new BusinessException("菜单状态必须为 0 或 1");
        }
        if (!isMenuType(req.getType())) {
            throw new BusinessException("菜单类型不合法");
        }
        Long pid = req.getPid() == null ? AdminConstants.ROOT_PARENT_ID : req.getPid();
        if (pid < 0) {
            throw new BusinessException("父菜单 ID 不能小于 0");
        }
        if (excludeId != null && excludeId.equals(pid)) {
            throw new BusinessException("父菜单不能是自身");
        }
        if (!AdminConstants.ROOT_PARENT_ID.equals(pid)) {
            SysMenu parent = getById(pid);
            if (parent == null || TYPE_BUTTON.equals(parent.getType())) {
                throw new BusinessException("父菜单不存在或不能作为父节点");
            }
        }
        if (isNameExists(req.getName(), excludeId)) {
            throw new BusinessException("菜单名称已存在：" + req.getName());
        }
        if (!TYPE_BUTTON.equals(req.getType()) && !StringUtils.hasText(req.getPath())) {
            throw new BusinessException("非按钮菜单的路由路径不能为空");
        }
        if (!TYPE_BUTTON.equals(req.getType()) && isPathExists(req.getPath(), excludeId)) {
            throw new BusinessException("菜单路径已存在：" + req.getPath());
        }
        if (TYPE_BUTTON.equals(req.getType()) && !StringUtils.hasText(req.getAuthCode())) {
            throw new BusinessException("按钮菜单的权限标识不能为空");
        }
        if (TYPE_EMBEDDED.equals(req.getType()) && !StringUtils.hasText(req.getIframeSrc())) {
            throw new BusinessException("内嵌菜单的内嵌地址不能为空");
        }
        if (TYPE_LINK.equals(req.getType()) && !StringUtils.hasText(req.getLink())) {
            throw new BusinessException("外链菜单的外链地址不能为空");
        }
    }

    private SysMenu normalize(MenuSaveReq req) {
        SysMenu menu = new SysMenu();
        BeanUtils.copyProperties(req, menu);
        menu.setPid(req.getPid() == null ? AdminConstants.ROOT_PARENT_ID : req.getPid());
        menu.setPlatformOnly(Boolean.TRUE.equals(req.getPlatformOnly()));
        if (TYPE_BUTTON.equals(menu.getType())) {
            menu.setPath(null);
            menu.setComponent(null);
            menu.setRedirect(null);
            menu.setIcon(null);
            menu.setActiveIcon(null);
            menu.setKeepAlive(null);
            menu.setHideInMenu(null);
            menu.setIframeSrc(null);
            menu.setLink(null);
            menu.setActivePath(null);
            menu.setAffixTab(null);
            menu.setBadge(null);
            menu.setBadgeType(null);
            menu.setBadgeVariants(null);
            menu.setHideChildrenInMenu(null);
            menu.setHideInBreadcrumb(null);
            menu.setHideInTab(null);
            return menu;
        }
        if (TYPE_CATALOG.equals(menu.getType()) || TYPE_MENU.equals(menu.getType())) {
            menu.setIframeSrc(null);
            menu.setLink(null);
        }
        if (TYPE_EMBEDDED.equals(menu.getType())) {
            menu.setLink(null);
        }
        if (TYPE_LINK.equals(menu.getType())) {
            menu.setIframeSrc(null);
            menu.setComponent(null);
        }
        if (TYPE_CATALOG.equals(menu.getType())) {
            menu.setAuthCode(null);
        }
        return menu;
    }

    private boolean isMenuType(String type) {
        return TYPE_CATALOG.equals(type) || TYPE_MENU.equals(type) || TYPE_BUTTON.equals(type)
            || TYPE_EMBEDDED.equals(type) || TYPE_LINK.equals(type);
    }

    private LambdaQueryWrapper<SysMenu> enabledMenusOrdered() {
        return new LambdaQueryWrapper<SysMenu>()
            .eq(SysMenu::getStatus, EntityStatus.ENABLED.getCode())
            .orderByAsc(SysMenu::getSort)
            .orderByAsc(SysMenu::getId);
    }

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
        MenuResp resp = new MenuResp();
        BeanUtils.copyProperties(menu, resp);
        return resp;
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
