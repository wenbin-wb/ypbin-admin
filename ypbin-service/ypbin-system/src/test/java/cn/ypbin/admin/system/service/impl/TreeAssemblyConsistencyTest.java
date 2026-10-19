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

        // ⚠️ 两条路径必须各用**独立**的 DeptResp 实例：它们共享同一批可变对象，
        // 后跑的 setChildren 会覆盖前者 ⇒ 断言退化成恒绿（由 iot 仓复核发现，本仓已修）。
        List<DeptResp> actualInputs = freshDepts(flat);
        List<DeptResp> actual = TreeUtils.build(actualInputs, DeptResp::getId, DeptResp::getPid,
            DeptResp::setChildren, AdminConstants.ROOT_PARENT_ID);
        // 与生产代码 SysDeptServiceImpl 相同的归一（叶子 children → null）。
        // 注意必须遍历**入参列表**（全部节点）：只遍历 roots 会漏掉深层叶子。
        actualInputs.forEach(node -> {
            if (node.getChildren() != null && node.getChildren().isEmpty()) {
                node.setChildren(null);
            }
        });
        List<DeptResp> expected = legacyDeptTree(freshDepts(flat), AdminConstants.ROOT_PARENT_ID);

        assertThat(describeDeptTree(actual))
            .as("迁移前后层级与顺序必须完全一致（顺序敏感：入参已按 sort,id 升序）")
            .isEqualTo(describeDeptTree(expected));

        // 单独钉住「叶子 children 归一」：这是本仓曾漏掉、由 iot 仓反向发现的对外形态变更。
        // DeptResp 带 @JsonInclude(NON_NULL) 只过滤 null、不过滤空集合 ⇒ 少这一步，
        // 部门接口会多出 "children":[]（迁移前是 null）。
        // 判据：归一的**唯一**合法结果是「children == null」；空集合即说明漏了归一。
        assertThat(flattenDept(actual))
            .as("children 只能是 null（叶子）或非空列表（有子节点）；空集合说明漏了归一，"
                + "会让部门接口多出 \"children\":[]，属对外响应形态变更")
            .allSatisfy(node -> assertThat(node.getChildren()).satisfiesAnyOf(
                children -> assertThat(children).isNull(),
                children -> assertThat(children).isNotEmpty()));
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

    /** 迁移前部门树递归实现的逐行复刻（仅用于对照，不进生产） */
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

    /**
     * 用同一份「扁平描述」构造**全新的**实例列表。
     *
     * <p>孪生对照必须各用独立实例：{@code DeptResp} 是可变的，两条路径若共享同一批对象，
     * 后跑的 {@code setChildren} 会覆盖前者 ⇒ 断言恒绿（由 iot 仓复核发现）。</p>
     *
     * @param flat 扁平节点（仅用于取 id/pid/name）
     * @return 全新实例列表
     */
    private static List<DeptResp> freshDepts(List<DeptResp> flat) {
        return flat.stream().map(d -> dept(d.getId(), d.getPid(), d.getName())).toList();
    }

    /**
     * 深度优先展平部门树（用于对「叶子 children 归一」做全量断言）。
     *
     * @param roots 根节点列表
     * @return 全部节点
     */
    private static List<DeptResp> flattenDept(List<DeptResp> roots) {
        List<DeptResp> all = new java.util.ArrayList<>();
        java.util.Deque<DeptResp> stack = new java.util.ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            DeptResp node = stack.pop();
            all.add(node);
            if (node.getChildren() != null) {
                node.getChildren().forEach(stack::push);
            }
        }
        return all;
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

    /**
     * <b>驱动真实生产代码</b>：直接调 {@code SysDeptServiceImpl#tree()}。
     *
     * <p><b>为什么必须补这条</b>（2026-10-19 变异验证发现）：上一条孪生对照用例是「复刻」生产写法
     * （自己调 {@code TreeUtils} 再自己做归一），因此<b>把生产代码里的归一删掉，它依然绿</b>——
     * 属假保护。本用例把 {@code list(...)} 桩成假数据，让真实的 {@code tree()} 跑一遍，
     * 删归一即转红（已变异验证）。</p>
     */
    @Test
    @DisplayName("生产路径：SysDeptServiceImpl#tree() 的叶子 children 必须是 null（删归一即转红）")
    void productionDeptTreeMustNormalizeLeafChildren() {
        SysDeptServiceImpl service = org.mockito.Mockito.spy(new SysDeptServiceImpl(
            org.mockito.Mockito.mock(cn.ypbin.admin.system.mapper.SysUserMapper.class),
            org.mockito.Mockito.mock(cn.ypbin.admin.system.mapper.SysRoleDeptMapper.class)));
        // 桩掉 MyBatis-Plus 的 list(...)：返回「总部 → 研发部 → 后端组」+ 一个无子节点的根
        List<cn.ypbin.admin.system.entity.SysDept> rows = new ArrayList<>();
        rows.add(sysDept(1L, 0L, "总部"));
        rows.add(sysDept(2L, 1L, "研发部"));
        rows.add(sysDept(4L, 2L, "后端组"));
        rows.add(sysDept(6L, 0L, "分公司"));
        org.mockito.Mockito.doReturn(rows).when(service)
            .list(org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        List<DeptResp> roots = service.tree();

        assertThat(roots).hasSize(2);
        // 深度优先展平后逐个断言：children 只能是 null 或非空（空集合 = 漏了归一）
        assertThat(flattenDept(roots))
            .as("生产路径的叶子 children 必须是 null；空集合会让部门接口多出 \"children\":[]")
            .allSatisfy(node -> assertThat(node.getChildren()).satisfiesAnyOf(
                children -> assertThat(children).isNull(),
                children -> assertThat(children).isNotEmpty()));
    }

    private static cn.ypbin.admin.system.entity.SysDept sysDept(Long id, Long pid, String name) {
        cn.ypbin.admin.system.entity.SysDept dept = new cn.ypbin.admin.system.entity.SysDept();
        dept.setId(id);
        dept.setPid(pid);
        dept.setName(name);
        dept.setSort(0);
        return dept;
    }
}
