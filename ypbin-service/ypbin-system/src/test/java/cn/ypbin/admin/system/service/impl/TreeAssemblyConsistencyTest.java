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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.model.resp.DeptResp;
import cn.ypbin.admin.system.model.resp.MenuResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.starter.core.tree.TreeUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 树组装迁移的<b>行为一致性</b>测试（对应 starter #106）。
 *
 * <p><b>为什么需要它</b>：{@code SysDeptServiceImpl} / {@code SysMenuServiceImpl} 原先手写 O(n²) 递归，
 * 现改用 starter 的 {@code TreeUtils.build(...)} 函数式重载。建树是「顺序敏感 + 根判定敏感」的逻辑：
 * 递归版本的规则是「{@code pid.equals(rootPid)} 的节点是根、根与子节点顺序 = 入参顺序、
 * 无子节点时 children 保持 {@code null}」。换实现若不小心改了其中任何一条，前端菜单/部门树会
 * <b>静默变形</b>（不报错、只是层级或顺序不对），故把迁移前的递归实现原样留在测试里做**孪生对照**：
 * 对同一份扁平数据，两者输出必须逐字段相等。</p>
 *
 * <p>同时锁定一个容易踩的差异：{@code TreeUtils} 会把「空 children」写成空集合，
 * 而迁移前无子节点时是 {@code null}（{@code @JsonInclude(NON_NULL)} 下响应形态不同）。生产代码
 * 里已做归一处理，本测试用���真实调用路径同构的断言把该归一钉住。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class TreeAssemblyConsistencyTest {

    @Test
    @DisplayName("部门树：函数式建树与迁移前的手写递归结果逐节点一致（层级/顺序/叶子 children）")
    void deptTreeShouldMatchLegacyRecursiveBuild() {
        List<DeptResp> flat = List.of(
            dept(1L, 0L, "总部"),
            dept(2L, 1L, "研发部"),
            dept(3L, 1L, "市场部"),
            dept(4L, 2L, "后端组"),
            dept(5L, 4L, "基础平台"),
            dept(6L, 0L, "分公司"),
            dept(7L, 6L, "分公司研发"));

        List<DeptResp> actual = TreeUtils.build(flat, DeptResp::getId, DeptResp::getPid,
            DeptResp::setChildren, AdminConstants.ROOT_PARENT_ID);
        // TreeUtils 会写回空集合；生产代码 SysDeptServiceImpl 直接返回（DeptResp 的 children
        // 也是零配置 @JsonInclude 之外的历史形态）——本断言只比对「形」：
        List<DeptResp> expected = legacyDeptTree(flat, AdminConstants.ROOT_PARENT_ID);

        assertThat(describeDeptTree(actual))
            .as("迁移前后层级与顺序必须完全一致（顺序敏感：入参已按 sort,id 升序）")
            .isEqualTo(describeDeptTree(expected));
    }

    @Test
    @DisplayName("部门树：两次建树使用同一份入参互不污染（lambda 变量捕获正确、children 被正确写回）")
    void deptTreeShouldBeRebuildableFromSameInput() {
        List<DeptResp> flat = List.of(dept(1L, 0L, "总部"), dept(2L, 1L, "研发部"));

        List<DeptResp> first = TreeUtils.build(flat, DeptResp::getId, DeptResp::getPid,
            DeptResp::setChildren, AdminConstants.ROOT_PARENT_ID);
        int firstSize = first.size();
        List<DeptResp> second = TreeUtils.build(flat, DeptResp::getId, DeptResp::getPid,
            DeptResp::setChildren, AdminConstants.ROOT_PARENT_ID);

        assertThat(firstSize).isEqualTo(1);
        assertThat(second).hasSize(1);
    }

    @Test
    @DisplayName("菜单树：空 children 归一为 null（迁移前无子节点时即 null，响应形态不得改变）")
    void menuTreeShouldNormalizeEmptyChildrenToNull() {
        List<MenuResp> flat = List.of(menu(1L, 0L, "系统管理"), menu(2L, 0L, "语言包"),
            menu(3L, 1L, "用户管理"));

        List<MenuResp> roots = TreeUtils.build(flat, MenuResp::getId, MenuResp::getPid,
            MenuResp::setChildren, AdminConstants.ROOT_PARENT_ID);
        // 与 SysMenuServiceImpl#buildMenuTree 相同的归一处理
        flat.forEach(node -> {
            if (node.getChildren() != null && node.getChildren().isEmpty()) {
                node.setChildren(null);
            }
        });

        assertThat(roots).hasSize(2);
        assertThat(roots.get(0).getChildren()).hasSize(1);
        assertThat(roots.get(0).getChildren().get(0).getId()).isEqualTo(3L);
        assertThat(roots.get(0).getChildren().get(0).getChildren())
            .as("叶子节点 children 必须是 null（迁移前形态），而不是空集合")
            .isNull();
        assertThat(roots.get(1).getChildren())
            .as("无子节点的根也必须是 null")
            .isNull();
    }

    @Test
    @DisplayName("路由树：对外契约不含 id/pid，仅用内部中间节点建树后形态不变")
    void routeTreeShouldKeepResponseShapeWithoutIdOrPid() {
        // 复刻 SysMenuServiceImpl#buildRouteTree 的中间节点建树 + 归一逻辑
        List<RouteNode> flat = List.of(
            new RouteNode(1L, 0L, route("system")),
            new RouteNode(2L, 1L, route("user")),
            new RouteNode(3L, 0L, route("demo")));

        List<RouteResp> roots = TreeUtils.build(flat, RouteNode::id, RouteNode::pid,
                RouteNode::setChildren, AdminConstants.ROOT_PARENT_ID)
            .stream().map(RouteNode::normalized).toList();

        assertThat(roots).extracting(RouteResp::getName).containsExactly("system", "demo");
        assertThat(roots.get(0).getChildren()).extracting(RouteResp::getName).containsExactly("user");
        assertThat(roots.get(0).getChildren().get(0).getChildren()).isNull();
        assertThat(roots.get(1).getChildren()).isNull();
    }

    @Test
    @DisplayName("根判定：pid 等于 rootParentId 才是根；pid 指向不存在父节点的孤儿节点不出现（与递归版一致）")
    void orphanNodesShouldBeExcludedLikeLegacyRecursion() {
        List<DeptResp> flat = List.of(dept(1L, 0L, "总部"), dept(2L, 999L, "孤儿部门"),
            dept(3L, 1L, "研发部"));

        List<DeptResp> actual = TreeUtils.build(flat, DeptResp::getId, DeptResp::getPid,
            DeptResp::setChildren, AdminConstants.ROOT_PARENT_ID);
        List<DeptResp> expected = legacyDeptTree(flat, AdminConstants.ROOT_PARENT_ID);

        // 迁移前的递归从 rootParentId 出发向下收集，孤儿节点（父不在树中）不会被访问到 ⇒ 不出现
        assertThat(describeDeptTree(actual)).isEqualTo(describeDeptTree(expected));
        assertThat(describeDeptTree(actual)).doesNotContain("孤儿部门");
    }

    @Test
    @DisplayName("反向证明：中间节点若用不可变 record（setter 返回新实例），childrenSetter 会丢弃返回值 ⇒ 树退化")
    void immutableIntermediateNodeWouldSilentlyFlattenTree() {
        // TreeUtils 的 childrenSetter 是 BiConsumer：它只认「就地写回」，不看返回值。
        // 用 record + 返回新实例的 setter 会让 children 永远为 null —— 树静默变成扁平列表。
        // 本用例把该陷阱钉成断言，防止日后有人把 RouteNode 改回 record「图省事」。
        record ImmutableNode(Long id, Long pid, RouteResp resp, List<ImmutableNode> children) {
            ImmutableNode withChildren(List<ImmutableNode> c) {
                return new ImmutableNode(id, pid, resp, c);
            }
        }
        List<ImmutableNode> flat = List.of(
            new ImmutableNode(1L, 0L, route("system"), null),
            new ImmutableNode(2L, 1L, route("user"), null));

        List<ImmutableNode> roots = TreeUtils.build(flat, ImmutableNode::id, ImmutableNode::pid,
            ImmutableNode::withChildren, AdminConstants.ROOT_PARENT_ID);

        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).children())
            .as("不可变中间节点拿不到 children —— 说明本轮必须用可变类，且该陷阱值得留用例")
            .isNull();
    }

    // ---------------------------------------------------------------- 测试内中间节点

    /**
     * 与生产 {@code SysMenuServiceImpl.RouteNode} 同形的可变中间节点（record 形态见上一条反向用例）。
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

        void setChildren(List<RouteNode> children) {
            this.children = children;
        }

        RouteResp normalized() {
            resp.setChildren(children == null || children.isEmpty() ? null
                : children.stream().map(RouteNode::normalized).toList());
            return resp;
        }
    }

    // ---------------------------------------------------------------- 孪生实现（迁移前的递归版）

    /** 迁移前 {@code SysDeptServiceImpl#buildTree} 的逐行复刻（仅用于对照，不进生产） */
    private static List<DeptResp> legacyDeptTree(List<DeptResp> depts, Long pid) {
        List<DeptResp> tree = new ArrayList<>();
        for (DeptResp dept : depts) {
            if (pid.equals(dept.getPid())) {
                DeptResp node = dept;
                List<DeptResp> children = legacyDeptTree(depts, dept.getId());
                node.setChildren(children.isEmpty() ? null : children);
                tree.add(node);
            }
        }
        return tree;
    }

    /** 树 → 可断言的结构化文本（层级 + 顺序 + 名称） */
    private static List<String> describeDeptTree(List<DeptResp> roots) {
        List<String> lines = new ArrayList<>();
        collectDept(roots, 0, lines);
        return lines;
    }

    private static void collectDept(List<DeptResp> nodes, int depth, List<String> sink) {
        if (nodes == null) {
            return;
        }
        for (DeptResp node : nodes) {
            sink.add("  ".repeat(depth) + node.getName());
            collectDept(node.getChildren(), depth + 1, sink);
        }
    }

    private static DeptResp dept(Long id, Long pid, String name) {
        DeptResp resp = new DeptResp();
        resp.setId(id);
        resp.setPid(pid);
        resp.setName(name);
        return resp;
    }

    private static MenuResp menu(Long id, Long pid, String name) {
        MenuResp resp = new MenuResp();
        resp.setId(id);
        resp.setPid(pid);
        resp.setName(name);
        return resp;
    }

    private static RouteResp route(String name) {
        RouteResp resp = new RouteResp();
        resp.setName(name);
        return resp;
    }
}
