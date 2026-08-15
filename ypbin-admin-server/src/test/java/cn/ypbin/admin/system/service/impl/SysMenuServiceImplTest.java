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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.system.model.resp.MenuResp;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.admin.system.service.SysPermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SysMenuServiceImpl} 菜单管理响应测试。
 *
 * @author wenbin
 * @since 2026-08-10
 */
class SysMenuServiceImplTest {

    private SysMenuServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new SysMenuServiceImpl(mock(SysPermissionService.class), mock(SysRoleMenuMapper.class),
            mock(SysAuthTemplateService.class)));
    }

    @Test
    void menuTreeKeepsHierarchyAndPlatformOnly() {
        doReturn(List.of(menu(1L, 0L, "SystemRoot", true), menu(2L, 1L, "SystemChild", false)))
            .when(service).list(any(LambdaQueryWrapper.class));

        List<MenuResp> tree = service.tree();

        assertThat(tree).hasSize(1);
        MenuResp root = tree.getFirst();
        assertThat(root.getId()).isEqualTo(1L);
        assertThat(root.getPlatformOnly()).isTrue();
        assertThat(root.getChildren()).hasSize(1);
        assertThat(root.getChildren().getFirst().getId()).isEqualTo(2L);
        assertThat(root.getChildren().getFirst().getPlatformOnly()).isFalse();
    }

    private SysMenu menu(Long id, Long pid, String name, boolean platformOnly) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setPid(pid);
        menu.setName(name);
        menu.setType("menu");
        menu.setPlatformOnly(platformOnly);
        return menu;
    }
}
