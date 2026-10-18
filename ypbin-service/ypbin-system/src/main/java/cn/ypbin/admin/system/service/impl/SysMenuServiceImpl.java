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

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.entity.SysRoleMenu;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.model.req.MenuSaveReq;
import cn.ypbin.admin.system.model.resp.MenuResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.tree.TreeUtils;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    private final SysUserRoleMapper userRoleMapper;
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
        evictMenuUsersCache(id);
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
        evictMenuUsersCache(id);
    }

    /**
     * 菜单权限标识/结构变更后，清除拥有该菜单（经角色）的所有用户的权限缓存（权限码可能已变）。
     *
     * <p>精确失效：只清受影响用户的 {@code sys:role:user:*}、{@code sys:perm:user:*}，不做整体清除。
     * 受影响用户可能很多，故合并键后一次性删除，避免逐个用户一次缓存往返。</p>
     */
    private void evictMenuUsersCache(Long menuId) {
        List<SysRoleMenu> roleMenus = roleMenuMapper.selectList(
            new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, menuId));
        if (roleMenus.isEmpty()) {
            return;
        }
        // 批量 IN 查询（禁循环内查库）：菜单→角色→用户 两级各一次查询，distinct 去重防重复清理
        List<Long> roleIds = roleMenus.stream().map(SysRoleMenu::getRoleId).distinct().toList();
        List<SysUserRole> userRoles = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, roleIds));
        SysCache.evictUserAuth(userRoles.stream().map(SysUserRole::getUserId).distinct().toList());
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
        if (excludeId != null) {
            // 新父节点不得为自身后代，防止成环后 buildRouteTree/buildMenuTree 递归爆栈
            rejectDescendantParent(pid, excludeId);
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

    /**
     * 校验把节点 {@code nodeId} 挂到新父节点 {@code newPid} 下是否会成环：
     * 沿 newPid 的 pid 链上溯，若途中遇到 nodeId 说明 nodeId 会成为自身祖先（即其自身后代），拒绝。
     * 全量菜单一次性加载后在内存判定，避免逐级 getById 的 N+1 查询。
     *
     * <p>本方法在事务内基于一次全量快照判定（防环判定先于更新执行），DB 无环约束且管理面
     * 操作低频，两笔并发迁移交错互移成环属极端低频，未额外加锁，判定漏网概率可接受。</p>
     *
     * @param newPid 新父节点 ID
     * @param nodeId 待移动节点 ID
     */
    private void rejectDescendantParent(Long newPid, Long nodeId) {
        if (AdminConstants.ROOT_PARENT_ID.equals(newPid)) {
            return;
        }
        Map<Long, Long> parentById = list().stream().collect(Collectors.toMap(
            SysMenu::getId,
            menu -> menu.getPid() == null ? AdminConstants.ROOT_PARENT_ID : menu.getPid(),
            (first, ignored) -> first));
        Long cursor = newPid;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && !AdminConstants.ROOT_PARENT_ID.equals(cursor) && visited.add(cursor)) {
            if (nodeId.equals(cursor)) {
                throw new BusinessException("父菜单不能选择自身或其子菜单");
            }
            cursor = parentById.get(cursor);
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

    /**
     * 扁平菜单 → 路由树（用 starter 的 O(n) 函数式建树，starter #106）。
     *
     * <p>原先手写递归在每一层都重新全量扫描入参列表（O(n²)）；{@code SysMenu} 的字段名是 {@code pid}、
     * 实现不了 starter 的 {@code TreeNode} 接口（要求 {@code getParentId()}），故用函数式重载——
     * 不必为「适配接口」在实体上加方法。</p>
     *
     * <p><b>为什么用内部 {@link RouteNode} 而不是直接对 {@link RouteResp} 建树</b>：
     * {@code RouteResp} 是<b>对外契约</b>（前端路由表），它没有 id/pid 字段；为建树而给它加两个字段
     * 会改变接口响应（多出前端用不到的 id/pid），属对外契约变更。故在内部用携带
     * 「(id, pid) + RouteResp」的私有 record 建树，对外形态一个字节都不变。</p>
     *
     * <p><b>行为一致性</b>（与迁移前逐条对齐）：根判定为「{@code pid} 等于 {@code rootParentId}」；
     * 同一父节点下的顺序 = 入参列表顺序（调用方已按 {@code sort, id} 升序查询）；
     * 迁移前也只在该节点有子节点时才 {@code setChildren}，无子节点时 children 保持 {@code null}——
     * 与 {@code TreeUtils} 的「一律写回（可能为空集合）」不同，故本方法在写回后把空集合归一为
     * {@code null}，保持响应不变（{@code @JsonInclude(NON_NULL)} 下 null 不序列化）。</p>
     *
     * @param menus        扁平菜单（调用方保证已按 sort,id 升序）
     * @param rootParentId 根节点的父 ID
     * @return 路由树
     */
    private List<RouteResp> buildRouteTree(List<SysMenu> menus, Long rootParentId) {
        List<RouteNode> flat = menus.stream()
            .map(menu -> new RouteNode(menu.getId(), menu.getPid(), toRoute(menu)))
            .toList();
        return TreeUtils.build(flat, RouteNode::id, RouteNode::pid, RouteNode::setChildren,
                rootParentId)
            .stream()
            .map(RouteNode::normalized)
            .toList();
    }

    /**
     * 建树用中间节点：携带树结构所需的 (id, pid) 与对外的 {@link RouteResp}。
     *
     * <p><b>为什么是可变类而不是 record</b>：{@code TreeUtils.build} 的 {@code childrenSetter} 是
     * {@link java.util.function.BiConsumer}——它<b>丢弃返回值</b>，只认「就地写回」。record 的
     * setter 式方法会返回新实例、原地对象不变，children 将永远为 {@code null}（树退化成扁平列表，
     * 且不报错）；这一点由 {@code TreeAssemblyConsistencyTest} 在测试侧以同样的 record 形态复现并拦住。</p>
     */
    private static final class RouteNode {

        private final Long id;

        private final Long pid;

        private final RouteResp resp;

        private List<RouteNode> children;

        RouteNode(Long id, Long pid, RouteResp resp) {
            this.id = id;
            this.pid = pid;
            this.resp = resp;
        }

        Long id() {
            return id;
        }

        Long pid() {
            return pid;
        }

        /**
         * 由 {@code TreeUtils} 回调写的 children（就地写回，必须返回值无关）。
         *
         * @param children 子节点
         */
        void setChildren(List<RouteNode> children) {
            this.children = children;
        }

        /**
         * 投影为对外节点：递归填充 children，并把「空集合」归一为 {@code null}
         * （迁移前无子节点时 children 就是 null，响应形态必须一致）。
         *
         * @return 对外路由节点
         */
        RouteResp normalized() {
            if (children == null || children.isEmpty()) {
                resp.setChildren(null);
            } else {
                resp.setChildren(children.stream().map(RouteNode::normalized).toList());
            }
            return resp;
        }
    }

    /**
     * 扁平菜单 → 菜单树（同上：starter 的 O(n) 函数式建树，根判定/顺序与迁移前一致）。
     *
     * @param menus        扁平菜单（调用方保证已按 sort,id 升序）
     * @param rootParentId 根节点的父 ID
     * @return 菜单树
     */
    private List<MenuResp> buildMenuTree(List<SysMenu> menus, Long rootParentId) {
        List<MenuResp> tree = menus.stream().map(this::toMenuResp).toList();
        List<MenuResp> roots = TreeUtils.build(tree, MenuResp::getId, MenuResp::getPid,
            MenuResp::setChildren, rootParentId);
        // 与迁移前一致：无子节点时 children 保持 null（TreeUtils 一律写回空集合），响应形态不变
        tree.forEach(node -> {
            if (node.getChildren() != null && node.getChildren().isEmpty()) {
                node.setChildren(null);
            }
        });
        return roots;
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
