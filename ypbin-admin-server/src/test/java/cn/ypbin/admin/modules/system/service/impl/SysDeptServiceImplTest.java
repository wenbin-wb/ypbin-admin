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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.admin.modules.system.entity.SysDept;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 部门子树展开测试。
 *
 * <p>锁住「按部门筛选要含全部下级」的语义，以及部门不存在、数据成环两种边界。</p>
 *
 * @author wenbin
 * @since 2026-11-17
 */
class SysDeptServiceImplTest {

    @Test
    @DisplayName("返回自身与全部下级部门")
    void returnsSelfAndDescendants() {
        List<SysDept> depts = List.of(
            dept(1L, 0L), dept(2L, 1L), dept(3L, 1L), dept(4L, 2L), dept(5L, 4L));

        assertThat(SysDeptServiceImpl.collectSubtreeIds(depts, 1L))
            .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
        assertThat(SysDeptServiceImpl.collectSubtreeIds(depts, 2L))
            .containsExactlyInAnyOrder(2L, 4L, 5L);
        assertThat(SysDeptServiceImpl.collectSubtreeIds(depts, 5L))
            .containsExactly(5L);
    }

    @Test
    @DisplayName("部门不存在时返回空集合")
    void returnsEmptyWhenDeptMissing() {
        List<SysDept> depts = List.of(dept(1L, 0L));

        assertThat(SysDeptServiceImpl.collectSubtreeIds(depts, 99L)).isEmpty();
        assertThat(SysDeptServiceImpl.collectSubtreeIds(depts, null)).isEmpty();
    }

    @Test
    @DisplayName("父指针成环时不会死循环")
    void survivesCyclicData() {
        List<SysDept> depts = List.of(dept(1L, 2L), dept(2L, 1L));

        assertThat(SysDeptServiceImpl.collectSubtreeIds(depts, 1L))
            .containsExactlyInAnyOrder(1L, 2L);
    }

    private static SysDept dept(Long id, Long pid) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setPid(pid);
        return dept;
    }
}
